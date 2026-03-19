import TanstackProvider from "@/src/global/providers/TanstackProvider";
import { ThemeProvider } from "@/src/global/providers/ThemeProvider";
import type { Metadata } from "next";
import React from "react";

import { Geist, Geist_Mono } from "next/font/google";

import { SidebarProvider } from "../global/shadcn/components/ui/sidebar";
import { TooltipProvider } from "../global/shadcn/components/ui/tooltip";
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
      { url: "/logo/favicon.ico", sizes: "any" },
      { url: "/logo/32x32.png", sizes: "32x16", type: "image/png" },
      { url: "/logo/32x32.png", sizes: "32x32", type: "image/png" },
    ],
    apple: [{ url: "logo/apple-icon.png", type: "image/png" }],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        <TanstackProvider>
          <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
            <TooltipProvider>
              <SidebarProvider
                className="flex-col"
                style={
                  {
                    "--sidebar-width": "17rem",
                    "--sidebar-width-icon": "2.5rem",
                  } as React.CSSProperties
                }
              >
                {children}
              </SidebarProvider>
            </TooltipProvider>
          </ThemeProvider>
        </TanstackProvider>
      </body>
    </html>
  );
}
