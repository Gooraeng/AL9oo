import type { Metadata } from "next";

import { Geist, Geist_Mono } from "next/font/google";

import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "AL9oo",
  description: "Reference Hub for Racing Master",
  icons: {
    icon: [
      { url: "logo/favicon.ico", sizes: "any"},
      { url: "logo/32x32.png", sizes: "32x16", type: "image/png" },
      { url: "logo/32x32.png", sizes: "32x32", type: "image/png" }
    ],
    apple: [{ url: "logo/apple-icon.png", type: "image/png"}]
  }
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        {children}
      </body>
    </html>
  );
}
