/** @type {import('next').NextConfig} */
module.exports = {
  reactStrictMode: true,
  compress: true,
  poweredByHeader: false,
  serverExternalPackages: [
    "firebase-admin",
    "mongoose",
    "@google-cloud/text-to-speech",
    "cloudinary",
  ],
};
