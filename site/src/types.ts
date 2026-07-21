/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export interface ErrorCode {
  id: string;
  code: string; // e.g. "E1", "F3", "H6"
  category: string; // e.g. "پکیج", "کولرگازی", "یخچال"
  brand: string; // e.g. "بوتان", "ال‌جی"
  model: string; // e.g. "کالدا ونزیا", "S8"
  title: string;
  description: string;
  causes: string[];
  steps: string[];
  precautions: string[];
  hazardLevel: 'low' | 'medium' | 'high' | 'critical';
  hazardDescription: string;
  toolsNeeded: string[];
  relatedParts: string[]; // SparePart IDs
  views: number;
  updatedBy?: string;
  isApproved: boolean;
  isVirtual?: boolean;
  isCommonProblem?: boolean;
  tags?: string[];

  // New fields requested for correct error_codes structure
  device_type?: string;
  error_code?: string;
  error_title?: string;
  compatible_models?: string[];
  solutions?: string[];
  ai_analysis?: string;
  technician_required?: boolean;
  created_at?: string;
  video_url?: string;
}

export type OrderStatus =
  | 'registered'    // ثبت شد
  | 'waiting'       // در انتظار تکنسین
  | 'accepted'      // پذیرفته شد
  | 'enroute'       // تکنسین در مسیر است
  | 'repairing'     // تعمیر در حال انجام
  | 'needs_part'    // نیاز به قطعه
  | 'completed'     // تکمیل شده
  | 'cancelled';    // لغو شده

export interface RepairOrder {
  id: string;
  customerName: string;
  customerPhone: string;
  city: string;
  region: string;
  address: string;
  category: string;
  brand: string;
  model: string;
  errorCode: string;
  description: string;
  status: OrderStatus;
  date: string;
  timeSlot: string;
  technicianId?: string;
  technicianName?: string;
  technicianPhone?: string;
  estimatedCost?: number;
  repairLog?: string;
  partsUsed?: Array<{
    partId: string;
    name: string;
    price: number;
    quantity: number;
  }>;
  mediaUrls?: string[];
  rating?: number;
  review?: string;
  createdAt: string;
}

export interface Technician {
  id: string;
  name: string;
  phone: string;
  password?: string;
  documents?: string[];
  specialty: string[]; // e.g. ["پکیج", "کولرگازی"]
  rating: number;
  completedOrders: number;
  balance: number; // Toman
  isVerified: boolean;
  activeLocation: string; // e.g. "تهران، منطقه ۵"
  avatarUrl?: string;
  satisfactionRate?: number;
}

export interface SparePart {
  id: string;
  name: string;
  description: string;
  price: number; // in Tomans
  image: string;
  category: string;
  compatibility: string[]; // Brands compatible with, e.g. ["بوتان", "ایران رادیاتور"]
  stock: number;
}

export interface PartPurchase {
  id: string;
  partId: string;
  partName: string;
  partCategory: string;
  customerName: string;
  customerPhone: string;
  customerAddress: string;
  price: number;
  date: string;
  status: 'pending' | 'shipped' | 'delivered';
}

export interface Notification {
  id: string;
  title: string;
  text: string;
  date: string;
  type: 'info' | 'success' | 'warning' | 'error' | 'sms';
  orderId?: string;
}

export interface Statistics {
  totalOrders: number;
  totalErrors: number;
  activeTechnicians: number;
  totalEarnings: number; // Commisions earned or overall revenue
  pendingApprovalsCount: number;
}

export interface CommonProblem {
  id: string;
  code: string;
  category: string;
  brand: string;
  model: string;
  title: string;
  description: string;
  causes: string[];
  steps: string[];
  precautions: string[];
  hazardLevel: 'low' | 'medium' | 'high' | 'critical';
  tags?: string[];
  views?: number;
  solutions?: string[];
}

export interface TicketMessage {
  sender: 'user' | 'staff';
  senderName: string;
  text: string;
  createdAt: string;
}

export interface SupportTicket {
  id: string;
  userPhone: string;
  userName: string;
  title: string;
  description: string;
  category: 'technical' | 'billing' | 'parts' | 'other';
  status: 'open' | 'pending' | 'resolved' | 'closed';
  priority: 'low' | 'medium' | 'high';
  createdAt: string;
  updatedAt: string;
  messages: TicketMessage[];
}

