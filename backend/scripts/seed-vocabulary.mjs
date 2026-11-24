/**
 * Idempotent seed script for English AI Vocabulary Learning Module.
 * Seeds the 13 core categories (+ subcategories) and 60 starter A1-A2 words
 * with accurate IPA pronunciation, Bengali meanings, and bilingual examples.
 *
 * Usage:
 *   MONGODB_URI="mongodb+srv://..." node scripts/seed-vocabulary.mjs
 */
import mongoose from "mongoose";

const uri = process.env.MONGODB_URI?.trim()?.replace(/^["']|["']$/g, "");
if (!uri) {
  console.error("ERROR: MONGODB_URI environment variable is required.");
  process.exit(1);
}

const categorySchema = new mongoose.Schema(
  {
    name: String,
    slug: { type: String, unique: true },
    description: String,
    icon: String,
    parentId: { type: mongoose.Schema.Types.ObjectId, default: null },
    sortOrder: Number,
    isActive: Boolean,
  },
  { timestamps: true }
);

const vocabSchema = new mongoose.Schema(
  {
    word: String,
    slug: { type: String, unique: true },
    meaning: String,
    pronunciation: String,
    audioUrl: { type: String, default: null },
    imageUrl: { type: String, default: null },
    partOfSpeech: String,
    level: String,
    categoryId: mongoose.Schema.Types.ObjectId,
    examples: [{ en: String, bn: String }],
    synonyms: [String],
    antonyms: [String],
    tags: [String],
    difficulty: Number,
    isPublished: Boolean,
    sortOrder: Number,
  },
  { timestamps: true }
);

const VocabularyCategory =
  mongoose.models.VocabularyCategory || mongoose.model("VocabularyCategory", categorySchema);
const Vocabulary = mongoose.models.Vocabulary || mongoose.model("Vocabulary", vocabSchema);

const PARENT_CATEGORIES = [
  { name: "Daily Life", slug: "daily-life", description: "Essential everyday actions and routines", icon: "wb_sunny", sortOrder: 1 },
  { name: "Communication", slug: "communication", description: "Speaking, listening, and expressing ideas", icon: "chat", sortOrder: 2 },
  { name: "Family", slug: "family", description: "Family members and home life", icon: "family_restroom", sortOrder: 3 },
  { name: "Work & Office", slug: "work-and-office", description: "Workplace tasks, schedules, and teamwork", icon: "work", sortOrder: 4 },
  { name: "Business", slug: "business", description: "Meetings, deals, and professional English", icon: "business_center", sortOrder: 5 },
  { name: "Shopping", slug: "shopping", description: "Buying, prices, and stores", icon: "shopping_bag", sortOrder: 6 },
  { name: "Travel", slug: "travel", description: "Trips, transport, and directions", icon: "flight", sortOrder: 7 },
  { name: "Food & Cooking", slug: "food-and-cooking", description: "Meals, ingredients, and kitchen verbs", icon: "restaurant", sortOrder: 8 },
  { name: "Health", slug: "health", description: "Wellness, doctor visits, and body care", icon: "favorite", sortOrder: 9 },
  { name: "Education", slug: "education", description: "School, study habits, and learning", icon: "school", sortOrder: 10 },
  { name: "Technology", slug: "technology", description: "Phones, internet, and digital life", icon: "devices", sortOrder: 11 },
  { name: "Emotions", slug: "emotions", description: "Feelings, moods, and reactions", icon: "mood", sortOrder: 12 },
  { name: "Relationships", slug: "relationships", description: "Friends, kindness, and social bonds", icon: "people", sortOrder: 13 },
];

const SUB_CATEGORIES = [
  { name: "Morning Routine", slug: "morning-routine", parentSlug: "daily-life", description: "Waking up, breakfast, and getting ready", icon: "alarm", sortOrder: 1 },
  { name: "Conversation Starters", slug: "conversation-starters", parentSlug: "communication", description: "Polite greetings and questions", icon: "forum", sortOrder: 2 },
  { name: "Airport & Hotel", slug: "airport-and-hotel", parentSlug: "travel", description: "Check-in, tickets, and stays", icon: "hotel", sortOrder: 3 },
  { name: "Office Meetings", slug: "office-meetings", parentSlug: "work-and-office", description: "Team discussions and deadlines", icon: "groups", sortOrder: 4 },
];

const WORDS = [
  // Daily Life (6)
  {
    word: "wake up", slug: "wake-up", meaning: "ঘুম থেকে ওঠা", pronunciation: "/weɪk ʌp/",
    partOfSpeech: "verb", level: "A1", categorySlug: "morning-routine", difficulty: 1,
    synonyms: ["get up", "awaken"], antonyms: ["fall asleep", "sleep"],
    examples: [
      { en: "I wake up at 6 a.m. every day.", bn: "আমি প্রতিদিন সকাল ৬টায় ঘুম থেকে উঠি।" },
      { en: "Please wake up early tomorrow.", bn: "দয়া করে আগামীকাল তাড়াতাড়ি ঘুম থেকে উঠো।" },
    ],
  },
  {
    word: "prepare", slug: "prepare", meaning: "প্রস্তুত করা", pronunciation: "/prɪˈpeər/",
    partOfSpeech: "verb", level: "A1", categorySlug: "daily-life", difficulty: 1,
    synonyms: ["get ready", "arrange"], antonyms: ["neglect"],
    examples: [
      { en: "I prepare my bag before school.", bn: "আমি স্কুলের আগে আমার ব্যাগ প্রস্তুত করি।" },
      { en: "She is preparing dinner now.", bn: "সে এখন রাতের খাবার তৈরি করছে।" },
    ],
  },
  {
    word: "clean", slug: "clean", meaning: "পরিষ্কার করা / পরিষ্কার", pronunciation: "/kliːn/",
    partOfSpeech: "verb", level: "A1", categorySlug: "daily-life", difficulty: 1,
    synonyms: ["wash", "tidy"], antonyms: ["dirty", "mess up"],
    examples: [
      { en: "We clean our room on Fridays.", bn: "আমরা শুক্রবারে আমাদের ঘর পরিষ্কার করি।" },
      { en: "Keep your desk clean and neat.", bn: "তোমার ডেস্ক পরিষ্কার ও পরিপাটি রাখো।" },
    ],
  },
  {
    word: "arrive", slug: "arrive", meaning: "পৌঁছানো", pronunciation: "/əˈraɪv/",
    partOfSpeech: "verb", level: "A1", categorySlug: "daily-life", difficulty: 1,
    synonyms: ["reach", "come"], antonyms: ["leave", "depart"],
    examples: [
      { en: "The train will arrive at 8 o'clock.", bn: "ট্রেনটি ৮টায় পৌঁছাবে।" },
      { en: "We arrived home safely.", bn: "আমরা নিরাপদে বাড়ি পৌঁছেছি।" },
    ],
  },
  {
    word: "borrow", slug: "borrow", meaning: "ধার নেওয়া", pronunciation: "/ˈbɒr.əʊ/",
    partOfSpeech: "verb", level: "A2", categorySlug: "daily-life", difficulty: 2,
    synonyms: ["take temporarily"], antonyms: ["lend", "return"],
    examples: [
      { en: "May I borrow your pen for a minute?", bn: "আমি কি এক মিনিটের জন্য তোমার কলমটা ধার নিতে পারি?" },
      { en: "She borrowed two books from the library.", bn: "সে লাইব্রেরি থেকে দুটি বই ধার নিয়েছে।" },
    ],
  },
  {
    word: "habit", slug: "habit", meaning: "অভ্যাস", pronunciation: "/ˈhæb.ɪt/",
    partOfSpeech: "noun", level: "A2", categorySlug: "daily-life", difficulty: 2,
    synonyms: ["routine", "practice"], antonyms: ["exception"],
    examples: [
      { en: "Reading every night is a good habit.", bn: "প্রতি রাতে পড়া একটি ভালো অভ্যাস।" },
      { en: "He wants to build a healthy morning habit.", bn: "সে একটি স্বাস্থ্যকর সকালের অভ্যাস গড়তে চায়।" },
    ],
  },
  // Communication (5)
  {
    word: "explain", slug: "explain", meaning: "ব্যাখ্যা করা", pronunciation: "/ɪkˈspleɪn/",
    partOfSpeech: "verb", level: "A1", categorySlug: "communication", difficulty: 1,
    synonyms: ["clarify", "describe"], antonyms: ["confuse"],
    examples: [
      { en: "Can you explain this word in Bangla?", bn: "আপনি কি এই শব্দটি বাংলায় ব্যাখ্যা করতে পারবেন?" },
      { en: "The teacher explained the lesson clearly.", bn: "শিক্ষক পাঠটি স্পষ্টভাবে বুঝিয়ে দিলেন।" },
    ],
  },
  {
    word: "listen", slug: "listen", meaning: "মনোযোগ দিয়ে শোনা", pronunciation: "/ˈlɪs.ən/",
    partOfSpeech: "verb", level: "A1", categorySlug: "conversation-starters", difficulty: 1,
    synonyms: ["hear", "pay attention"], antonyms: ["ignore"],
    examples: [
      { en: "Listen carefully to the pronunciation.", bn: "উচ্চারণটি মনোযোগ দিয়ে শুনুন।" },
      { en: "Good friends listen to each other.", bn: "ভালো বন্ধুরা একে অপরের কথা শোনে।" },
    ],
  },
  {
    word: "repeat", slug: "repeat", meaning: "পুনরায় বলা বা করা", pronunciation: "/rɪˈpiːt/",
    partOfSpeech: "verb", level: "A1", categorySlug: "communication", difficulty: 1,
    synonyms: ["say again", "redo"], antonyms: ["stop"],
    examples: [
      { en: "Could you repeat that sentence, please?", bn: "দয়া করে বাক্যটি কি আবার বলবেন?" },
      { en: "Repeat each word aloud three times.", bn: "প্রতিটি শব্দ জোরে তিনবার বলো।" },
    ],
  },
  {
    word: "polite", slug: "polite", meaning: "ভদ্র / বিনয়ী", pronunciation: "/pəˈlaɪt/",
    partOfSpeech: "adjective", level: "A2", categorySlug: "conversation-starters", difficulty: 2,
    synonyms: ["courteous", "respectful"], antonyms: ["rude", "impolite"],
    examples: [
      { en: "Saying thank you is a polite habit.", bn: "ধন্যবাদ বলা একটি ভদ্র অভ্যাস।" },
      { en: "She gave a polite reply to the guest.", bn: "সে অতিথিকে ভদ্র উত্তর দিল।" },
    ],
  },
  {
    word: "agree", slug: "agree", meaning: "একমত হওয়া", pronunciation: "/əˈɡriː/",
    partOfSpeech: "verb", level: "A1", categorySlug: "communication", difficulty: 1,
    synonyms: ["accept", "consent"], antonyms: ["disagree", "refuse"],
    examples: [
      { en: "I completely agree with your idea.", bn: "আমি তোমার ধারণার সাথে পুরোপুরি একমত।" },
      { en: "Do they agree on the meeting time?", bn: "তারা কি মিটিংয়ের সময় নিয়ে একমত?" },
    ],
  },
  // Family (4)
  {
    word: "parents", slug: "parents", meaning: "মা-বাবা", pronunciation: "/ˈpeə.rənts/",
    partOfSpeech: "noun", level: "A1", categorySlug: "family", difficulty: 1,
    synonyms: ["mother and father"], antonyms: ["children"],
    examples: [
      { en: "My parents live in Dhaka.", bn: "আমার মা-বাবা ঢাকায় থাকেন।" },
      { en: "Respect and care for your parents.", bn: "তোমার মা-বাবাকে সম্মান ও যত্ন করো।" },
    ],
  },
  {
    word: "relative", slug: "relative", meaning: "আত্মীয়", pronunciation: "/ˈrel.ə.tɪv/",
    partOfSpeech: "noun", level: "A2", categorySlug: "family", difficulty: 2,
    synonyms: ["family member", "kin"], antonyms: ["stranger"],
    examples: [
      { en: "Many relatives visited us during Eid.", bn: "ঈদের সময় অনেক আত্মীয় আমাদের বাড়িতে এসেছিলেন।" },
      { en: "She is a close relative of mine.", bn: "তিনি আমার একজন কাছের আত্মীয়।" },
    ],
  },
  {
    word: "daughter", slug: "daughter", meaning: "কন্যা / মেয়ে", pronunciation: "/ˈdɔː.tər/",
    partOfSpeech: "noun", level: "A1", categorySlug: "family", difficulty: 1,
    synonyms: ["girl child"], antonyms: ["son"],
    examples: [
      { en: "Their daughter studies at a primary school.", bn: "তাদের মেয়ে একটি প্রাথমিক বিদ্যালয়ে পড়ে।" },
      { en: "He is very proud of his daughter.", bn: "তিনি তাঁর মেয়েকে নিয়ে খুব গর্বিত।" },
    ],
  },
  {
    word: "together", slug: "together", meaning: "একসাথে", pronunciation: "/təˈɡeð.ər/",
    partOfSpeech: "adverb", level: "A1", categorySlug: "family", difficulty: 1,
    synonyms: ["jointly", "side by side"], antonyms: ["apart", "alone"],
    examples: [
      { en: "Our family eats dinner together.", bn: "আমাদের পরিবার একসাথে রাতের খাবার খায়।" },
      { en: "Let us practice English together.", bn: "চলো আমরা একসাথে ইংরেজি চর্চা করি।" },
    ],
  },
  // Work & Office (5)
  {
    word: "schedule", slug: "schedule", meaning: "সময়সূচী", pronunciation: "/ˈʃedʒ.uːl/",
    partOfSpeech: "noun", level: "A2", categorySlug: "office-meetings", difficulty: 2,
    synonyms: ["timetable", "plan"], antonyms: ["disorder"],
    examples: [
      { en: "Check today's schedule before noon.", bn: "দুপুরের আগে আজকের সময়সূচী দেখে নাও।" },
      { en: "My work schedule is very busy this week.", bn: "এই সপ্তাহে আমার কাজের সময়সূচী খুব ব্যস্ত।" },
    ],
  },
  {
    word: "colleague", slug: "colleague", meaning: "সহকর্মী", pronunciation: "/ˈkɒl.iːɡ/",
    partOfSpeech: "noun", level: "A2", categorySlug: "work-and-office", difficulty: 2,
    synonyms: ["coworker", "teammate"], antonyms: ["competitor"],
    examples: [
      { en: "My colleague helped me finish the report.", bn: "আমার সহকর্মী প্রতিবেদনটি শেষ করতে আমাকে সাহায্য করেছে।" },
      { en: "She gets along well with all her colleagues.", bn: "সে তার সব সহকর্মীর সাথে ভালোভাবে মিশে।" },
    ],
  },
  {
    word: "finish", slug: "finish", meaning: "শেষ করা", pronunciation: "/ˈfɪn.ɪʃ/",
    partOfSpeech: "verb", level: "A1", categorySlug: "work-and-office", difficulty: 1,
    synonyms: ["complete", "end"], antonyms: ["start", "begin"],
    examples: [
      { en: "I will finish this task by 5 p.m.", bn: "আমি বিকেল ৫টার মধ্যে এই কাজটি শেষ করব।" },
      { en: "Did you finish reading the email?", bn: "তুমি কি ইমেইলটি পড়া শেষ করেছ?" },
    ],
  },
  {
    word: "deadline", slug: "deadline", meaning: "শেষ সময়সীমা", pronunciation: "/ˈded.laɪn/",
    partOfSpeech: "noun", level: "A2", categorySlug: "office-meetings", difficulty: 2,
    synonyms: ["time limit", "due date"], antonyms: ["extension"],
    examples: [
      { en: "The project deadline is next Monday.", bn: "প্রকল্পের শেষ সময়সীমা আগামী সোমবার।" },
      { en: "We must submit the form before the deadline.", bn: "আমাদের শেষ সময়সীমার আগে ফর্মটি জমা দিতে হবে।" },
    ],
  },
  {
    word: "break", slug: "break", meaning: "বিরতি", pronunciation: "/breɪk/",
    partOfSpeech: "noun", level: "A1", categorySlug: "work-and-office", difficulty: 1,
    synonyms: ["rest", "pause"], antonyms: ["continuation"],
    examples: [
      { en: "Let us take a ten-minute tea break.", bn: "চলো ১০ মিনিটের চায়ের বিরতি নিই।" },
      { en: "You look tired; you need a short break.", bn: "তোমাকে ক্লান্ত দেখাচ্ছে; তোমার একটি ছোট বিরতি দরকার।" },
    ],
  },
  // Business (4)
  {
    word: "customer", slug: "customer", meaning: "ক্রেতা / গ্রাহক", pronunciation: "/ˈkʌs.tə.mər/",
    partOfSpeech: "noun", level: "A1", categorySlug: "business", difficulty: 1,
    synonyms: ["client", "buyer"], antonyms: ["seller"],
    examples: [
      { en: "Always greet every customer with a smile.", bn: "সব সময় প্রতিটি ক্রেতাকে হাসিমুখে স্বাগত জানান।" },
      { en: "The customer asked about the delivery date.", bn: "গ্রাহক ডেলিভারির তারিখ সম্পর্কে জিজ্ঞাসা করলেন।" },
    ],
  },
  {
    word: "meeting", slug: "meeting", meaning: "সভা / মিটিং", pronunciation: "/ˈmiː.tɪŋ/",
    partOfSpeech: "noun", level: "A1", categorySlug: "business", difficulty: 1,
    synonyms: ["conference", "session"], antonyms: ["dismissal"],
    examples: [
      { en: "Our team meeting starts at 10 a.m.", bn: "আমাদের দলের মিটিং সকাল ১০টায় শুরু হয়।" },
      { en: "She prepared slides for the client meeting.", bn: "সে ক্লায়েন্ট মিটিংয়ের জন্য স্লাইড তৈরি করেছে।" },
    ],
  },
  {
    word: "confirm", slug: "confirm", meaning: "নিশ্চিত করা", pronunciation: "/kənˈfɜːm/",
    partOfSpeech: "verb", level: "A2", categorySlug: "business", difficulty: 2,
    synonyms: ["verify", "approve"], antonyms: ["cancel", "deny"],
    examples: [
      { en: "Please confirm your order by email.", bn: "দয়া করে ইমেইলের মাধ্যমে আপনার অর্ডার নিশ্চিত করুন।" },
      { en: "He called to confirm the appointment.", bn: "সে অ্যাপয়েন্টমেন্ট নিশ্চিত করতে ফোন করেছিল।" },
    ],
  },
  {
    word: "profit", slug: "profit", meaning: "লাভ / মুনাফা", pronunciation: "/ˈprɒf.ɪt/",
    partOfSpeech: "noun", level: "A2", categorySlug: "business", difficulty: 2,
    synonyms: ["gain", "earnings"], antonyms: ["loss"],
    examples: [
      { en: "The small shop made a good profit this month.", bn: "ছোট দোকানটি এই মাসে ভালো লাভ করেছে।" },
      { en: "Smart planning helps increase business profit.", bn: "সঠিক পরিকল্পনা ব্যবসার লাভ বাড়াতে সাহায্য করে।" },
    ],
  },
  // Shopping (4)
  {
    word: "price", slug: "price", meaning: "দাম / মূল্য", pronunciation: "/praɪs/",
    partOfSpeech: "noun", level: "A1", categorySlug: "shopping", difficulty: 1,
    synonyms: ["cost", "rate"], antonyms: ["free"],
    examples: [
      { en: "What is the price of this shirt?", bn: "এই শার্টটির দাম কত?" },
      { en: "The price is very reasonable here.", bn: "এখানে দাম খুবই যুক্তিসঙ্গত।" },
    ],
  },
  {
    word: "cheap", slug: "cheap", meaning: "সস্তা / কম দামি", pronunciation: "/tʃiːp/",
    partOfSpeech: "adjective", level: "A1", categorySlug: "shopping", difficulty: 1,
    synonyms: ["inexpensive", "affordable"], antonyms: ["expensive", "costly"],
    examples: [
      { en: "Fresh vegetables are cheap in this market.", bn: "এই বাজারে তাজা শাকসবজি সস্তা।" },
      { en: "It is cheap, but the quality is good.", bn: "এটি সস্তা, তবে মান ভালো।" },
    ],
  },
  {
    word: "discount", slug: "discount", meaning: "ছাড় / মূল্যহ্রাস", pronunciation: "/ˈdɪs.kaʊnt/",
    partOfSpeech: "noun", level: "A2", categorySlug: "shopping", difficulty: 2,
    synonyms: ["reduction", "offer"], antonyms: ["surcharge"],
    examples: [
      { en: "There is a twenty percent discount today.", bn: "আজ ২০ শতাংশ ছাড় চলছে।" },
      { en: "Students get a special discount on books.", bn: "শিক্ষার্থীরা বইয়ের ওপর বিশেষ ছাড় পায়।" },
    ],
  },
  {
    word: "receipt", slug: "receipt", meaning: "রসিদ / বিল", pronunciation: "/rɪˈsiːt/",
    partOfSpeech: "noun", level: "A2", categorySlug: "shopping", difficulty: 2,
    synonyms: ["bill", "slip"], antonyms: [],
    examples: [
      { en: "Please keep the receipt after paying.", bn: "টাকা দেওয়ার পর দয়া করে রসিদটি রাখুন।" },
      { en: "Can I have a receipt for my purchase?", bn: "আমি কি আমার কেনাকাটার একটি রসিদ পেতে পারি?" },
    ],
  },
  // Travel (5)
  {
    word: "ticket", slug: "ticket", meaning: "টিকিট", pronunciation: "/ˈtɪk.ɪt/",
    partOfSpeech: "noun", level: "A1", categorySlug: "airport-and-hotel", difficulty: 1,
    synonyms: ["pass", "boarding pass"], antonyms: [],
    examples: [
      { en: "I booked a bus ticket to Sylhet.", bn: "আমি সিলেটের একটি বাসের টিকিট বুক করেছি।" },
      { en: "Show your ticket at the gate.", bn: "গেটে আপনার টিকিট দেখান।" },
    ],
  },
  {
    word: "luggage", slug: "luggage", meaning: "মালপত্র / ব্যাগ", pronunciation: "/ˈlʌɡ.ɪdʒ/",
    partOfSpeech: "noun", level: "A2", categorySlug: "airport-and-hotel", difficulty: 2,
    synonyms: ["baggage", "suitcases"], antonyms: [],
    examples: [
      { en: "Do not leave your luggage unattended.", bn: "আপনার মালপত্র অযত্নে ফেলে রাখবেন না।" },
      { en: "How heavy is your luggage?", bn: "আপনার লাগেজের ওজন কত?" },
    ],
  },
  {
    word: "journey", slug: "journey", meaning: "ভ্রমণ / যাত্রা", pronunciation: "/ˈdʒɜː.ni/",
    partOfSpeech: "noun", level: "A2", categorySlug: "travel", difficulty: 2,
    synonyms: ["trip", "voyage"], antonyms: ["stay"],
    examples: [
      { en: "Have a safe and happy journey!", bn: "আপনার যাত্রা নিরাপদ ও আনন্দময় হোক!" },
      { en: "The train journey took five hours.", bn: "ট্রেন যাত্রায় পাঁচ ঘণ্টা সময় লেগেছিল।" },
    ],
  },
  {
    word: "direction", slug: "direction", meaning: "দিকনির্দেশনা", pronunciation: "/daɪˈrek.ʃən/",
    partOfSpeech: "noun", level: "A2", categorySlug: "travel", difficulty: 2,
    synonyms: ["way", "route"], antonyms: [],
    examples: [
      { en: "Excuse me, could you give me directions to the station?", bn: "ক্ষমা করবেন, আমাকে কি স্টেশনে যাওয়ার পথ বলে দেবেন?" },
      { en: "We walked in the direction of the river.", bn: "আমরা নদীর দিকে হাঁটছিলাম।" },
    ],
  },
  {
    word: "reserve", slug: "reserve", meaning: "আগে থেকে বুক বা সংরক্ষণ করা", pronunciation: "/rɪˈzɜːv/",
    partOfSpeech: "verb", level: "A2", categorySlug: "airport-and-hotel", difficulty: 2,
    synonyms: ["book", "hold"], antonyms: ["cancel"],
    examples: [
      { en: "I would like to reserve a room for two nights.", bn: "আমি দুই রাতের জন্য একটি রুম বুক করতে চাই।" },
      { en: "Let us reserve a table near the window.", bn: "চলো জানালার পাশে একটি টেবিল বুক করি।" },
    ],
  },
  // Food & Cooking (5)
  {
    word: "delicious", slug: "delicious", meaning: "সুস্বাদু", pronunciation: "/dɪˈlɪʃ.əs/",
    partOfSpeech: "adjective", level: "A1", categorySlug: "food-and-cooking", difficulty: 1,
    synonyms: ["tasty", "yummy"], antonyms: ["tasteless", "awful"],
    examples: [
      { en: "My mother cooks delicious biryani.", bn: "আমার মা সুস্বাদু বিরিয়ানি রান্না করেন।" },
      { en: "This mango juice tastes delicious!", bn: "এই আমের জুসটা খেতে দারুণ সুস্বাদু!" },
    ],
  },
  {
    word: "breakfast", slug: "breakfast", meaning: "সকালের নাশতা", pronunciation: "/ˈbrek.fəst/",
    partOfSpeech: "noun", level: "A1", categorySlug: "food-and-cooking", difficulty: 1,
    synonyms: ["morning meal"], antonyms: ["dinner"],
    examples: [
      { en: "Never skip breakfast before work.", bn: "কাজের আগে কখনো সকালের নাশতা বাদ দিও না।" },
      { en: "We had eggs and bread for breakfast.", bn: "আমরা সকালের নাশতায় ডিম ও রুটি খেয়েছি।" },
    ],
  },
  {
    word: "hungry", slug: "hungry", meaning: "ক্ষুধার্ত", pronunciation: "/ˈhʌŋ.ɡri/",
    partOfSpeech: "adjective", level: "A1", categorySlug: "food-and-cooking", difficulty: 1,
    synonyms: ["starving"], antonyms: ["full"],
    examples: [
      { en: "I am very hungry after the long walk.", bn: "লম্বা হাঁটার পর আমার খুব ক্ষুধা লেগেছে।" },
      { en: "Are you hungry? Let us eat something.", bn: "তোমার কি ক্ষুধা লেগেছে? চলো কিছু খাই।" },
    ],
  },
  {
    word: "boil", slug: "boil", meaning: "ফোটানো / সিদ্ধ করা", pronunciation: "/bɔɪl/",
    partOfSpeech: "verb", level: "A2", categorySlug: "food-and-cooking", difficulty: 2,
    synonyms: ["simmer", "heat"], antonyms: ["freeze", "cool"],
    examples: [
      { en: "Boil the water before making tea.", bn: "চা বানানোর আগে পানি ফুটিয়ে নাও।" },
      { en: "She boiled two eggs for breakfast.", bn: "সে নাশতার জন্য দুটি ডিম সিদ্ধ করেছে।" },
    ],
  },
  {
    word: "fresh", slug: "fresh", meaning: "তাজা / সতেজ", pronunciation: "/freʃ/",
    partOfSpeech: "adjective", level: "A1", categorySlug: "food-and-cooking", difficulty: 1,
    synonyms: ["new", "crisp"], antonyms: ["stale", "old"],
    examples: [
      { en: "Drink plenty of fresh water every day.", bn: "প্রতিদিন প্রচুর বিশুদ্ধ পানি পান করো।" },
      { en: "They sell fresh fish in the morning.", bn: "তারা সকালে তাজা মাছ বিক্রি করে।" },
    ],
  },
  // Health (5)
  {
    word: "healthy", slug: "healthy", meaning: "স্বাস্থ্যকর / সুস্থ", pronunciation: "/ˈhel.θi/",
    partOfSpeech: "adjective", level: "A1", categorySlug: "health", difficulty: 1,
    synonyms: ["fit", "well"], antonyms: ["sick", "unhealthy"],
    examples: [
      { en: "Eating fruits keeps us healthy.", bn: "ফল খাওয়া আমাদের সুস্থ রাখে।" },
      { en: "Walking daily is a healthy exercise.", bn: "প্রতিদিন হাঁটা একটি স্বাস্থ্যকর ব্যায়াম।" },
    ],
  },
  {
    word: "medicine", slug: "medicine", meaning: "ওষুধ", pronunciation: "/ˈmed.ɪ.sən/",
    partOfSpeech: "noun", level: "A1", categorySlug: "health", difficulty: 1,
    synonyms: ["remedy", "drug"], antonyms: [],
    examples: [
      { en: "Take this medicine after meals.", bn: "খাবারের পর এই ওষুধটি খাবেন।" },
      { en: "Did you buy the medicine from the pharmacy?", bn: "তুমি কি ফার্মেসি থেকে ওষুধ কিনেছ?" },
    ],
  },
  {
    word: "fever", slug: "fever", meaning: "জ্বর", pronunciation: "/ˈfiː.vər/",
    partOfSpeech: "noun", level: "A1", categorySlug: "health", difficulty: 1,
    synonyms: ["high temperature"], antonyms: [],
    examples: [
      { en: "He has a mild fever today.", bn: "আজ তার হালকা জ্বর আছে।" },
      { en: "Rest and drink water when you have a fever.", bn: "জ্বর হলে বিশ্রাম নাও এবং পানি পান করো।" },
    ],
  },
  {
    word: "exercise", slug: "exercise", meaning: "ব্যায়াম / অনুশীলন", pronunciation: "/ˈek.sə.saɪz/",
    partOfSpeech: "noun", level: "A1", categorySlug: "health", difficulty: 1,
    synonyms: ["workout", "practice"], antonyms: ["inactivity"],
    examples: [
      { en: "Morning exercise gives me energy.", bn: "সকালের ব্যায়াম আমাকে শক্তি দেয়।" },
      { en: "Try to exercise for thirty minutes daily.", bn: "প্রতিদিন ৩০ মিনিট ব্যায়াম করার চেষ্টা করো।" },
    ],
  },
  {
    word: "appointment", slug: "appointment", meaning: "সাক্ষাতের নির্ধারিত সময়", pronunciation: "/əˈpɔɪnt.mənt/",
    partOfSpeech: "noun", level: "A2", categorySlug: "health", difficulty: 2,
    synonyms: ["booking", "visit"], antonyms: [],
    examples: [
      { en: "I have a doctor's appointment at 4 p.m.", bn: "বিকেল ৪টায় ডাক্তারের সাথে আমার অ্যাপয়েন্টমেন্ট আছে।" },
      { en: "Book an appointment before visiting the clinic.", bn: "ক্লিনিকে যাওয়ার আগে অ্যাপয়েন্টমেন্ট বুক করুন।" },
    ],
  },
  // Education (5)
  {
    word: "practice", slug: "practice", meaning: "অনুশীলন করা / চর্চা", pronunciation: "/ˈpræk.tɪs/",
    partOfSpeech: "verb", level: "A1", categorySlug: "education", difficulty: 1,
    synonyms: ["train", "rehearse"], antonyms: ["neglect"],
    examples: [
      { en: "Practice speaking English for ten minutes a day.", bn: "দিনে ১০ মিনিট ইংরেজি বলার অনুশীলন করো।" },
      { en: "With regular practice, you will improve fast.", bn: "নিয়মিত চর্চায় তুমি দ্রুত উন্নতি করবে।" },
    ],
  },
  {
    word: "improve", slug: "improve", meaning: "উন্নতি করা", pronunciation: "/ɪmˈpruːv/",
    partOfSpeech: "verb", level: "A2", categorySlug: "education", difficulty: 2,
    synonyms: ["get better", "enhance"], antonyms: ["worsen"],
    examples: [
      { en: "Learning five words daily will improve your English.", bn: "প্রতিদিন ৫টি শব্দ শিখলে তোমার ইংরেজির উন্নতি হবে।" },
      { en: "His pronunciation improved a lot this month.", bn: "এই মাসে তার উচ্চারণের অনেক উন্নতি হয়েছে।" },
    ],
  },
  {
    word: "mistake", slug: "mistake", meaning: "ভুল", pronunciation: "/mɪˈsteɪk/",
    partOfSpeech: "noun", level: "A1", categorySlug: "education", difficulty: 1,
    synonyms: ["error", "slip"], antonyms: ["accuracy", "success"],
    examples: [
      { en: "Do not be afraid of making mistakes.", bn: "ভুল করতে ভয় পেয়ো না।" },
      { en: "We learn new things from every mistake.", bn: "আমরা প্রতিটি ভুল থেকে নতুন কিছু শিখি।" },
    ],
  },
  {
    word: "remember", slug: "remember", meaning: "মনে রাখা", pronunciation: "/rɪˈmem.bər/",
    partOfSpeech: "verb", level: "A1", categorySlug: "education", difficulty: 1,
    synonyms: ["recall", "keep in mind"], antonyms: ["forget"],
    examples: [
      { en: "Review words often so you remember them.", bn: "শব্দগুলো বারবার রিভিউ করো যাতে মনে থাকে।" },
      { en: "Do you remember the meaning of this word?", bn: "তোমার কি এই শব্দটির অর্থ মনে আছে?" },
    ],
  },
  {
    word: "library", slug: "library", meaning: "গ্রন্থাগার / লাইব্রেরি", pronunciation: "/ˈlaɪ.brər.i/",
    partOfSpeech: "noun", level: "A1", categorySlug: "education", difficulty: 1,
    synonyms: ["reading room"], antonyms: [],
    examples: [
      { en: "It is quiet inside the university library.", bn: "বিশ্ববিদ্যালয়ের লাইব্রেরির ভেতর বেশ শান্ত।" },
      { en: "I study at the library every afternoon.", bn: "আমি প্রতিদিন বিকেলে লাইব্রেরিতে পড়াশোনা করি।" },
    ],
  },
  // Technology (4)
  {
    word: "download", slug: "download", meaning: "ডাউনলোড করা", pronunciation: "/ˈdaʊn.ləʊd/",
    partOfSpeech: "verb", level: "A1", categorySlug: "technology", difficulty: 1,
    synonyms: ["save", "fetch"], antonyms: ["upload"],
    examples: [
      { en: "You can download the study notes for free.", bn: "আপনি বিনামূল্যে স্টাডি নোট ডাউনলোড করতে পারেন।" },
      { en: "Wait until the update finishes downloading.", bn: "আপডেটটি ডাউনলোড শেষ হওয়া পর্যন্ত অপেক্ষা করুন।" },
    ],
  },
  {
    word: "password", slug: "password", meaning: "পাসওয়ার্ড / গোপন সংকেত", pronunciation: "/ˈpɑːs.wɜːd/",
    partOfSpeech: "noun", level: "A1", categorySlug: "technology", difficulty: 1,
    synonyms: ["passcode", "PIN"], antonyms: [],
    examples: [
      { en: "Never share your password with anyone.", bn: "কখনো আপনার পাসওয়ার্ড কারো সাথে শেয়ার করবেন না।" },
      { en: "Choose a strong password for your account.", bn: "আপনার অ্যাকাউন্টের জন্য একটি শক্তিশালী পাসওয়ার্ড বেছে নিন।" },
    ],
  },
  {
    word: "search", slug: "search", meaning: "খোঁজা / অনুসন্ধান করা", pronunciation: "/sɜːtʃ/",
    partOfSpeech: "verb", level: "A1", categorySlug: "technology", difficulty: 1,
    synonyms: ["look for", "find"], antonyms: ["ignore"],
    examples: [
      { en: "Search for the word in the dictionary.", bn: "অভিধানে শব্দটি খুঁজে দেখো।" },
      { en: "She searched online for English speaking tips.", bn: "সে অনলাইনে ইংরেজি বলার টিপস খুঁজেছে।" },
    ],
  },
  {
    word: "connect", slug: "connect", meaning: "সংযুক্ত করা / হওয়া", pronunciation: "/kəˈnekt/",
    partOfSpeech: "verb", level: "A2", categorySlug: "technology", difficulty: 2,
    synonyms: ["link", "join"], antonyms: ["disconnect"],
    examples: [
      { en: "Connect your phone to Wi-Fi before updating.", bn: "আপডেট করার আগে আপনার ফোন ওয়াই-ফাইয়ের সাথে সংযুক্ত করুন।" },
      { en: "The internet helps us connect with people worldwide.", bn: "ইন্টারনেট আমাদের সারা বিশ্বের মানুষের সাথে যুক্ত হতে সাহায্য করে।" },
    ],
  },
  // Emotions (4)
  {
    word: "confident", slug: "confident", meaning: "আত্মবিশ্বাসী", pronunciation: "/ˈkɒn.fɪ.dənt/",
    partOfSpeech: "adjective", level: "A2", categorySlug: "emotions", difficulty: 2,
    synonyms: ["sure", "self-assured"], antonyms: ["nervous", "shy"],
    examples: [
      { en: "I feel more confident speaking English now.", bn: "আমি এখন ইংরেজিতে কথা বলতে বেশি আত্মবিশ্বাসী বোধ করি।" },
      { en: "Speak slowly and sound confident.", bn: "ধীরে কথা বলো এবং আত্মবিশ্বাসী শোনাও।" },
    ],
  },
  {
    word: "excited", slug: "excited", meaning: "উৎসাহিত / আনন্দিত", pronunciation: "/ɪkˈsaɪ.tɪd/",
    partOfSpeech: "adjective", level: "A1", categorySlug: "emotions", difficulty: 1,
    synonyms: ["thrilled", "eager"], antonyms: ["bored", "calm"],
    examples: [
      { en: "The children are excited about the picnic.", bn: "বাচ্চারা পিকনিক নিয়ে খুব উৎসাহিত।" },
      { en: "I am excited to learn five new words today.", bn: "আমি আজ ৫টি নতুন শব্দ শিখতে উৎসাহিত।" },
    ],
  },
  {
    word: "nervous", slug: "nervous", meaning: "উদ্বিগ্ন / নার্ভাস", pronunciation: "/ˈnɜː.vəs/",
    partOfSpeech: "adjective", level: "A2", categorySlug: "emotions", difficulty: 2,
    synonyms: ["anxious", "worried"], antonyms: ["calm", "confident"],
    examples: [
      { en: "Do not feel nervous before your interview.", bn: "ইন্টারভিউয়ের আগে নার্ভাস বোধ করো না।" },
      { en: "Take a deep breath when you feel nervous.", bn: "নার্ভাস লাগলে লম্বা শ্বাস নাও।" },
    ],
  },
  {
    word: "proud", slug: "proud", meaning: "গর্বিত", pronunciation: "/praʊd/",
    partOfSpeech: "adjective", level: "A1", categorySlug: "emotions", difficulty: 1,
    synonyms: ["honored", "pleased"], antonyms: ["ashamed"],
    examples: [
      { en: "Be proud of your daily progress.", bn: "তোমার প্রতিদিনের উন্নতি নিয়ে গর্বিত হও।" },
      { en: "Her family is proud of her hard work.", bn: "তাঁর পরিবার তাঁর কঠোর পরিশ্রমের জন্য গর্বিত।" },
    ],
  },
  // Relationships (4)
  {
    word: "friend", slug: "friend", meaning: "বন্ধু", pronunciation: "/frend/",
    partOfSpeech: "noun", level: "A1", categorySlug: "relationships", difficulty: 1,
    synonyms: ["companion", "pal"], antonyms: ["enemy"],
    examples: [
      { en: "A true friend helps you in hard times.", bn: "একজন প্রকৃত বন্ধু কঠিন সময়ে সাহায্য করে।" },
      { en: "I met my old friend at the book fair.", bn: "বইমেলায় আমার পুরোনো বন্ধুর সাথে দেখা হয়েছিল।" },
    ],
  },
  {
    word: "trust", slug: "trust", meaning: "বিশ্বাস / ভরসা করা", pronunciation: "/trʌst/",
    partOfSpeech: "verb", level: "A2", categorySlug: "relationships", difficulty: 2,
    synonyms: ["believe in", "rely on"], antonyms: ["doubt", "distrust"],
    examples: [
      { en: "Friendship grows when people trust each other.", bn: "মানুষ একে অপরকে বিশ্বাস করলে বন্ধুত্ব বাড়ে।" },
      { en: "You can trust him with this responsibility.", bn: "আপনি এই দায়িত্বে তার ওপর ভরসা করতে পারেন।" },
    ],
  },
  {
    word: "kind", slug: "kind", meaning: "দয়ালু / সহৃদয়", pronunciation: "/kaɪnd/",
    partOfSpeech: "adjective", level: "A1", categorySlug: "relationships", difficulty: 1,
    synonyms: ["gentle", "caring"], antonyms: ["cruel", "unkind"],
    examples: [
      { en: "Thank you for your kind words.", bn: "আপনার সুন্দর কথার জন্য ধন্যবাদ।" },
      { en: "Always be kind to animals and neighbors.", bn: "সব সময় প্রাণী এবং প্রতিবেশীদের প্রতি দয়ালু হও।" },
    ],
  },
  {
    word: "help", slug: "help", meaning: "সাহায্য করা", pronunciation: "/help/",
    partOfSpeech: "verb", level: "A1", categorySlug: "relationships", difficulty: 1,
    synonyms: ["assist", "support"], antonyms: ["harm"],
    examples: [
      { en: "Could you help me carry this box?", bn: "আপনি কি এই বাক্সটি বহন করতে আমাকে সাহায্য করবেন?" },
      { en: "This app helps me learn English step by step.", bn: "এই অ্যাপটি আমাকে ধাপে ধাপে ইংরেজি শিখতে সাহায্য করে।" },
    ],
  },
];

async function main() {
  await mongoose.connect(uri);
  console.log("Connected to MongoDB. Seeding vocabulary categories...");

  const catIdBySlug = new Map();
  for (const cat of PARENT_CATEGORIES) {
    const doc = await VocabularyCategory.findOneAndUpdate(
      { slug: cat.slug },
      { $set: { ...cat, parentId: null, isActive: true } },
      { upsert: true, new: true }
    );
    catIdBySlug.set(cat.slug, doc._id);
  }

  for (const sub of SUB_CATEGORIES) {
    const parentId = catIdBySlug.get(sub.parentSlug) || null;
    const doc = await VocabularyCategory.findOneAndUpdate(
      { slug: sub.slug },
      {
        $set: {
          name: sub.name,
          slug: sub.slug,
          description: sub.description,
          icon: sub.icon,
          parentId,
          sortOrder: sub.sortOrder,
          isActive: true,
        },
      },
      { upsert: true, new: true }
    );
    catIdBySlug.set(sub.slug, doc._id);
  }

  console.log(`Seeded ${catIdBySlug.size} categories. Seeding ${WORDS.length} words...`);
  let order = 1;
  for (const w of WORDS) {
    const categoryId = catIdBySlug.get(w.categorySlug) || catIdBySlug.get("daily-life");
    await Vocabulary.findOneAndUpdate(
      { slug: w.slug },
      {
        $set: {
          word: w.word,
          slug: w.slug,
          meaning: w.meaning,
          pronunciation: w.pronunciation,
          partOfSpeech: w.partOfSpeech,
          level: w.level,
          categoryId,
          examples: w.examples,
          synonyms: w.synonyms,
          antonyms: w.antonyms,
          tags: [w.level.toLowerCase(), w.categorySlug],
          difficulty: w.difficulty,
          isPublished: true,
          sortOrder: order++,
        },
      },
      { upsert: true, new: true }
    );
  }

  console.log(`Done! Seeded ${catIdBySlug.size} categories and ${WORDS.length} A1-A2 words.`);
  await mongoose.disconnect();
}

main().catch((err) => {
  console.error("Seed failed:", err);
  process.exit(1);
});
