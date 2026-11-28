import { AppError } from "@/lib/errors";
import { hasStudentPremiumAccess } from "@/lib/courseUtils";
import {
  generateSslCommerzTranId,
  getSslCommerzConfig,
  initiateSslCommerzSession,
  resolveCourseCheckoutAmount,
  validateSslCommerzOrder,
  verifySslCommerzValidationPayload,
} from "@/lib/sslcommerz";
import { assertCourseObjectId } from "@/modules/courses/course.service";
import Course from "@/models/Course";
import CoursePurchase from "@/models/CoursePurchase";

function formatPurchaseDoc(doc) {
  if (!doc) return null;
  return {
    purchaseId: String(doc._id),
    tranId: doc.tranId,
    courseId: String(doc.courseId),
    courseTitle: doc.courseTitle || "",
    gateway: doc.gateway || "sslcommerz",
    status: doc.status,
    amount: Number(doc.amount || 0),
    currency: doc.currency || "BDT",
    cardType: doc.cardType || "",
    cardIssuer: doc.cardIssuer || "",
    bankTranId: doc.bankTranId || "",
    failureReason: doc.failureReason || "",
    paidAt: doc.paidAt ? new Date(doc.paidAt).toISOString() : null,
    createdAt: doc.createdAt ? new Date(doc.createdAt).toISOString() : null,
  };
}

export async function initCourseSslCommerzPayment({ userDoc, courseId, cusPhone = "", origin = "" }) {
  const oid = assertCourseObjectId(courseId, "courseId");
  const course = await Course.findOne({ _id: oid, isPublished: true }).lean();
  if (!course) {
    throw new AppError(404, "COURSE_NOT_FOUND", "Published course not found.");
  }
  if (!course.isPremium) {
    throw new AppError(400, "COURSE_IS_FREE", "This course is free and does not require payment.");
  }

  const alreadyHasGlobal = hasStudentPremiumAccess(userDoc);
  const existingPaid = await CoursePurchase.exists({
    userId: userDoc.firebaseUid,
    courseId: oid,
    status: "paid",
  });
  if (alreadyHasGlobal || existingPaid) {
    throw new AppError(409, "ALREADY_PURCHASED", "You already have full access to this premium course.");
  }

  const config = getSslCommerzConfig();
  if (!config.configured) {
    throw new AppError(
      503,
      "PAYMENT_GATEWAY_NOT_CONFIGURED",
      "SSLCOMMERZ payment gateway is not configured on the server. Set SSLCOMMERZ_STORE_ID and SSLCOMMERZ_STORE_PASSWORD."
    );
  }
  if (!origin) {
    throw new AppError(500, "ORIGIN_UNAVAILABLE", "Server callback URL could not be resolved.");
  }

  const amount = resolveCourseCheckoutAmount(course, config.defaultPrice);
  const currency = config.currency;
  const tranId = generateSslCommerzTranId(String(course._id));
  const cleanPhone = String(cusPhone || "").replace(/[^0-9+]/g, "").slice(0, 20) || "01700000000";

  const purchase = await CoursePurchase.create({
    userId: userDoc.firebaseUid,
    userObjectId: userDoc._id,
    courseId: course._id,
    courseTitle: course.title,
    tranId,
    gateway: "sslcommerz",
    status: "pending",
    amount,
    currency,
  });

  const callbackBase = `${origin}/api/payments/sslcommerz/callback`;
  const successUrl = `${callbackBase}?outcome=success&tran_id=${encodeURIComponent(tranId)}`;
  const failUrl = `${callbackBase}?outcome=fail&tran_id=${encodeURIComponent(tranId)}`;
  const cancelUrl = `${callbackBase}?outcome=cancel&tran_id=${encodeURIComponent(tranId)}`;
  const ipnUrl = `${origin}/api/payments/sslcommerz/ipn`;

  try {
    const session = await initiateSslCommerzSession({
      config,
      payload: {
        store_id: config.storeId,
        store_passwd: config.storePassword,
        total_amount: amount.toFixed(2),
        currency,
        tran_id: tranId,
        success_url: successUrl,
        fail_url: failUrl,
        cancel_url: cancelUrl,
        ipn_url: ipnUrl,
        shipping_method: "NO",
        num_of_item: "1",
        product_name: String(course.title || "Premium English Course").slice(0, 100),
        product_category: String(course.category || "Education").slice(0, 50),
        product_profile: "non-physical-goods",
        cus_name: String(userDoc.name || "Student").slice(0, 50),
        cus_email: String(userDoc.email || "student@englishai.app").slice(0, 50),
        cus_add1: "Dhaka",
        cus_city: "Dhaka",
        cus_postcode: "1200",
        cus_country: "Bangladesh",
        cus_phone: cleanPhone,
        value_a: String(course._id),
        value_b: userDoc.firebaseUid,
      },
    });

    await CoursePurchase.updateOne(
      { _id: purchase._id },
      { $set: { sessionKey: session.sessionKey, gatewayUrl: session.gatewayUrl } }
    );

    return {
      purchaseId: String(purchase._id),
      tranId,
      courseId: String(course._id),
      courseTitle: course.title,
      amount,
      currency,
      gatewayUrl: session.gatewayUrl,
      isSandbox: config.isSandbox,
    };
  } catch (err) {
    await CoursePurchase.updateOne(
      { _id: purchase._id },
      {
        $set: {
          status: "failed",
          failureReason: String(err?.message || "Gateway init failed").slice(0, 350),
        },
      }
    );
    throw new AppError(502, "PAYMENT_INIT_FAILED", err?.message || "Unable to initiate SSLCOMMERZ payment.");
  }
}

async function verifyAndFinalizePaidOrder(purchase, valId) {
  if (purchase.status === "paid") {
    return purchase;
  }
  const config = getSslCommerzConfig();
  if (!config.configured) {
    throw new AppError(503, "PAYMENT_GATEWAY_NOT_CONFIGURED", "SSLCOMMERZ credentials missing on server.");
  }
  if (!valId) {
    await CoursePurchase.updateOne(
      { _id: purchase._id, status: { $ne: "paid" } },
      { $set: { status: "validation_failed", failureReason: "Missing val_id from SSLCOMMERZ callback." } }
    );
    return CoursePurchase.findById(purchase._id).lean();
  }

  const validationData = await validateSslCommerzOrder({ config, valId });
  const check = verifySslCommerzValidationPayload(validationData, purchase);

  if (!check.valid) {
    await CoursePurchase.updateOne(
      { _id: purchase._id, status: { $ne: "paid" } },
      {
        $set: {
          status: "validation_failed",
          valId: String(valId),
          failureReason: check.reason.slice(0, 350),
        },
      }
    );
    return CoursePurchase.findById(purchase._id).lean();
  }

  const now = new Date();
  await CoursePurchase.updateOne(
    { _id: purchase._id, status: { $ne: "paid" } },
    {
      $set: {
        status: "paid",
        valId: check.valId,
        bankTranId: check.bankTranId,
        cardType: check.cardType,
        cardIssuer: check.cardIssuer,
        storeAmount: check.storeAmount,
        failureReason: "",
        paidAt: now,
        validatedAt: now,
      },
    }
  );

  return CoursePurchase.findById(purchase._id).lean();
}

export async function processSslCommerzCallback({ outcome, tranId, valId, errorMessage = "" }) {
  const cleanTranId = String(tranId || "").trim();
  if (!cleanTranId) {
    return { status: "invalid", message: "Missing transaction reference.", purchase: null };
  }

  const purchase = await CoursePurchase.findOne({ tranId: cleanTranId }).lean();
  if (!purchase) {
    return { status: "not_found", message: "Order not found.", purchase: null };
  }

  if (purchase.status === "paid") {
    return { status: "paid", message: "Payment verified! Course unlocked.", purchase: formatPurchaseDoc(purchase) };
  }

  const normalizedOutcome = String(outcome || "").trim().toLowerCase();
  if (normalizedOutcome === "success") {
    const updated = await verifyAndFinalizePaidOrder(purchase, valId);
    if (updated?.status === "paid") {
      return {
        status: "paid",
        message: "Payment verified! Your premium course is now unlocked.",
        purchase: formatPurchaseDoc(updated),
      };
    }
    return {
      status: updated?.status || "validation_failed",
      message: updated?.failureReason || "Could not validate payment with SSLCOMMERZ.",
      purchase: formatPurchaseDoc(updated),
    };
  }

  if (normalizedOutcome === "cancel") {
    await CoursePurchase.updateOne(
      { _id: purchase._id, status: "pending" },
      { $set: { status: "cancelled", failureReason: "Payment cancelled by student." } }
    );
    const updated = await CoursePurchase.findById(purchase._id).lean();
    return { status: "cancelled", message: "Payment was cancelled.", purchase: formatPurchaseDoc(updated) };
  }

  const reason = String(errorMessage || "Payment failed at gateway.").slice(0, 350);
  await CoursePurchase.updateOne(
    { _id: purchase._id, status: "pending" },
    { $set: { status: "failed", failureReason: reason } }
  );
  const updated = await CoursePurchase.findById(purchase._id).lean();
  return { status: "failed", message: reason, purchase: formatPurchaseDoc(updated) };
}

export async function processSslCommerzIpn({ params = {} }) {
  const tranId = String(params.tran_id || "").trim();
  const valId = String(params.val_id || "").trim();
  const ipnStatus = String(params.status || "").trim().toUpperCase();

  if (!tranId) {
    throw new AppError(400, "INVALID_IPN", "Missing tran_id in IPN payload.");
  }

  const purchase = await CoursePurchase.findOne({ tranId }).lean();
  if (!purchase) {
    throw new AppError(404, "ORDER_NOT_FOUND", "Purchase record not found.");
  }

  if (ipnStatus === "VALID" || ipnStatus === "VALIDATED") {
    const updated = await verifyAndFinalizePaidOrder(purchase, valId);
    return { received: true, purchase: formatPurchaseDoc(updated) };
  }

  if (ipnStatus === "CANCELLED" && purchase.status === "pending") {
    await CoursePurchase.updateOne(
      { _id: purchase._id, status: "pending" },
      { $set: { status: "cancelled", failureReason: "Cancelled via SSLCOMMERZ IPN." } }
    );
  } else if (ipnStatus === "FAILED" && purchase.status === "pending") {
    await CoursePurchase.updateOne(
      { _id: purchase._id, status: "pending" },
      { $set: { status: "failed", failureReason: String(params.error || "Failed via SSLCOMMERZ IPN.").slice(0, 350) } }
    );
  }

  const latest = await CoursePurchase.findById(purchase._id).lean();
  return { received: true, purchase: formatPurchaseDoc(latest) };
}

export async function getSslCommerzPurchaseStatus({ userDoc, tranId }) {
  const cleanTranId = String(tranId || "").trim();
  if (!cleanTranId) {
    throw new AppError(400, "INVALID_TRAN_ID", "tranId query parameter is required.");
  }

  const purchase = await CoursePurchase.findOne({
    tranId: cleanTranId,
    userId: userDoc.firebaseUid,
  }).lean();

  if (!purchase) {
    throw new AppError(404, "PURCHASE_NOT_FOUND", "Course purchase record not found.");
  }

  const hasAccess = purchase.status === "paid" || hasStudentPremiumAccess(userDoc);
  const message =
    purchase.status === "paid"
      ? "Payment verified! Course is unlocked."
      : purchase.status === "cancelled"
        ? "Payment was cancelled."
        : purchase.status === "failed" || purchase.status === "validation_failed"
          ? purchase.failureReason || "Payment could not be completed."
          : "Payment is still pending verification.";

  return {
    ...formatPurchaseDoc(purchase),
    hasAccess,
    message,
  };
}

export async function listStudentCoursePurchases({ userDoc }) {
  const docs = await CoursePurchase.find({ userId: userDoc.firebaseUid })
    .sort({ createdAt: -1 })
    .limit(50)
    .lean();

  return {
    purchases: docs.map(formatPurchaseDoc),
  };
}
