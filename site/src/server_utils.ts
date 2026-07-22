import crypto from "crypto";
import fs from "fs";
import path from "path";

const JWT_SECRET = process.env.JWT_SECRET || "kadyar24-secure-jwt-hmac-sha256-key-998811";

// ----------------------------------------------------
// 1. JWT & REFRESH TOKENS (Custom Cryptographic Signatures)
// ----------------------------------------------------
export interface TokenPayload {
  userId: string;
  phone: string;
  role: string;
  isSuperAdmin?: boolean;
}

export function generateAccessToken(payload: TokenPayload): string {
  const header = { alg: "HS256", typ: "JWT" };
  // Access Token expires in 1 hour
  const exp = Math.floor(Date.now() / 1000) + 3600; 
  const data = { ...payload, exp };

  const h64 = Buffer.from(JSON.stringify(header)).toString("base64url");
  const p64 = Buffer.from(JSON.stringify(data)).toString("base64url");

  const signature = crypto
    .createHmac("sha256", JWT_SECRET)
    .update(`${h64}.${p64}`)
    .digest("base64url");

  return `${h64}.${p64}.${signature}`;
}

export function generateRefreshToken(payload: TokenPayload): string {
  const header = { alg: "HS256", typ: "JWT" };
  // Refresh Token expires in 30 days
  const exp = Math.floor(Date.now() / 1000) + 30 * 24 * 3600; 
  const data = { userId: payload.userId, isRefresh: true, exp };

  const h64 = Buffer.from(JSON.stringify(header)).toString("base64url");
  const p64 = Buffer.from(JSON.stringify(data)).toString("base64url");

  const signature = crypto
    .createHmac("sha256", JWT_SECRET)
    .update(`${h64}.${p64}`)
    .digest("base64url");

  return `${h64}.${p64}.${signature}`;
}

export function verifyToken(token: string): any {
  try {
    const [h64, p64, signature] = token.split(".");
    if (!h64 || !p64 || !signature) return null;

    const expectedSignature = crypto
      .createHmac("sha256", JWT_SECRET)
      .update(`${h64}.${p64}`)
      .digest("base64url");

    if (signature !== expectedSignature) return null;

    const data = JSON.parse(Buffer.from(p64, "base64url").toString("utf8"));
    if (data.exp && Math.floor(Date.now() / 1000) > data.exp) {
      return null; // Token expired
    }
    return data;
  } catch {
    return null;
  }
}

// ----------------------------------------------------
// 2. RATE LIMITER & ACCOUNT LOCKOUT TRACKER
// ----------------------------------------------------
interface RateLimitTracker {
  count: number;
  resetTime: number;
}

interface LockoutTracker {
  failedCount: number;
  lockedUntil: number;
}

const rateLimitMap = new Map<string, RateLimitTracker>();
const loginLockoutMap = new Map<string, LockoutTracker>();

export function checkRateLimit(ip: string, limit: number = 30, windowMs: number = 60000): { allowed: boolean; remaining: number } {
  const now = Date.now();
  const tracker = rateLimitMap.get(ip);

  if (!tracker || now > tracker.resetTime) {
    rateLimitMap.set(ip, { count: 1, resetTime: now + windowMs });
    return { allowed: true, remaining: limit - 1 };
  }

  tracker.count++;
  const remaining = Math.max(0, limit - tracker.count);
  if (tracker.count > limit) {
    return { allowed: false, remaining: 0 };
  }

  return { allowed: true, remaining };
}

export function getLoginLockoutStatus(phone: string): { locked: boolean; timeLeftMs: number } {
  const tracker = loginLockoutMap.get(phone);
  if (!tracker) return { locked: false, timeLeftMs: 0 };

  const now = Date.now();
  if (now < tracker.lockedUntil) {
    return { locked: true, timeLeftMs: tracker.lockedUntil - now };
  }

  // If lockout expired, clear it
  if (now >= tracker.lockedUntil && tracker.lockedUntil > 0) {
    loginLockoutMap.delete(phone);
  }
  return { locked: false, timeLeftMs: 0 };
}

export function recordFailedLogin(phone: string): { locked: boolean; failedCount: number } {
  const now = Date.now();
  let tracker = loginLockoutMap.get(phone);

  if (!tracker) {
    tracker = { failedCount: 1, lockedUntil: 0 };
  } else {
    tracker.failedCount++;
  }

  if (tracker.failedCount >= 5) {
    // Lock for 15 minutes
    tracker.lockedUntil = now + 15 * 60 * 1000;
  }

  loginLockoutMap.set(phone, tracker);
  return { locked: tracker.failedCount >= 5, failedCount: tracker.failedCount };
}

export function clearFailedLogins(phone: string): void {
  loginLockoutMap.delete(phone);
}

// ----------------------------------------------------
// 3. EXCEL & PDF EXPORT GENERATORS (Persian / UTF-8 compatible)
// ----------------------------------------------------
export function generateExcelCSV(headers: string[], rows: any[][]): string {
  // UTF-8 BOM is required for MS Excel to correctly read Persian script on double click
  const BOM = "\uFEFF";
  const content = rows
    .map((row) =>
      row
        .map((val) => {
          const str = String(val === null || val === undefined ? "" : val);
          // Escape quotes and wrap with quotes if comma or newline is present
          if (str.includes(",") || str.includes("\n") || str.includes("\"")) {
            return `"${str.replace(/"/g, '""')}"`;
          }
          return str;
        })
        .join(",")
    )
    .join("\n");
  return BOM + headers.join(",") + "\n" + content;
}

export function generatePrintPDFHTML(title: string, headers: string[], rows: any[][]): string {
  const tableHeaders = headers.map((h) => `<th style="padding: 12px; border: 1px solid #cbd5e1; background-color: #f1f5f9; text-align: right;">${h}</th>`).join("");
  const tableRows = rows
    .map(
      (row) =>
        `<tr>${row
          .map((cell) => `<td style="padding: 10px; border: 1px solid #e2e8f0; font-size: 11px;">${cell === null || cell === undefined ? "-" : cell}</td>`)
          .join("")}</tr>`
    )
    .join("");

  return `
    <!DOCTYPE html>
    <html dir="rtl" lang="fa">
    <head>
      <meta charset="UTF-8">
      <title>${title}</title>
      <style>
        body { font-family: Tahoma, Arial, sans-serif; padding: 30px; color: #1e293b; background-color: #fff; }
        .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #3b82f6; padding-bottom: 15px; margin-bottom: 25px; }
        .title { font-size: 18px; font-weight: bold; color: #1e3a8a; }
        .date { font-size: 12px; color: #64748b; }
        table { width: 100%; border-collapse: collapse; margin-top: 15px; }
        .footer { margin-top: 40px; text-align: center; font-size: 10px; color: #94a3b8; border-top: 1px solid #e2e8f0; padding-top: 15px; }
        @media print {
          .no-print { display: none; }
          body { padding: 0; }
        }
      </style>
    </head>
    <body>
      <div class="no-print" style="margin-bottom: 20px; text-align: left;">
        <button onclick="window.print()" style="padding: 10px 20px; background-color: #2563eb; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: bold;">🖨️ چاپ سند / خروجی PDF</button>
      </div>
      <div class="header">
        <div class="title">سامانه هوشمند کدیار۲۴ - گزارش رسمی ${title}</div>
        <div class="date">تاریخ گزارش: ${new Date().toLocaleDateString("fa-IR")}</div>
      </div>
      <table>
        <thead>
          <tr>${tableHeaders}</tr>
        </thead>
        <tbody>
          ${tableRows}
        </tbody>
      </table>
      <div class="footer">
        این گزارش به صورت سیستمی و معتبر از سامانه کدیار۲۴ استخراج شده است. هرگونه جعل یا سوءاستفاده پیگرد قانونی دارد.
      </div>
    </body>
    </html>
  `;
}
