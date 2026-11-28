import mongoose from "mongoose";

const coursePurchaseSchema = new mongoose.Schema(
  {
    userId: { type: String, required: true, index: true }, // Firebase UID
    userObjectId: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true, index: true },
    courseId: { type: mongoose.Schema.Types.ObjectId, ref: "Course", required: true, index: true },
    courseTitle: { type: String, default: "", trim: true, maxlength: 200 },
    tranId: { type: String, required: true, unique: true, index: true },
    gateway: { type: String, enum: ["sslcommerz"], default: "sslcommerz" },
    status: {
      type: String,
      enum: ["pending", "paid", "failed", "cancelled", "validation_failed"],
      default: "pending",
      index: true,
    },
    amount: { type: Number, required: true, min: 0 },
    currency: { type: String, default: "BDT", uppercase: true, trim: true },
    sessionKey: { type: String, default: "" },
    gatewayUrl: { type: String, default: "" },
    valId: { type: String, default: "" },
    bankTranId: { type: String, default: "" },
    cardType: { type: String, default: "" },
    cardIssuer: { type: String, default: "" },
    storeAmount: { type: Number, default: 0 },
    failureReason: { type: String, default: "", trim: true, maxlength: 400 },
    paidAt: { type: Date, default: null },
    validatedAt: { type: Date, default: null },
  },
  { timestamps: true }
);

coursePurchaseSchema.index({ userId: 1, courseId: 1, status: 1 });
coursePurchaseSchema.index({ userId: 1, createdAt: -1 });

export default mongoose.models.CoursePurchase || mongoose.model("CoursePurchase", coursePurchaseSchema);
