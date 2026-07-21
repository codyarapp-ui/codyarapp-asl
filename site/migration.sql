-- ----------------------------------------------------------------------------------
-- IRAN-SERVICE SYSTEM UPGRADE DATABASE MIGRATION SCRIPT (MySQL / cPanel Compatible)
-- ----------------------------------------------------------------------------------
-- This script provisions the complete, robust, normalization-standard relational schema
-- with Foreign Key relationships, security logs, settings parameters, indexes,
-- and subscription controls.
-- ----------------------------------------------------------------------------------

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `activity_logs`;
DROP TABLE IF EXISTS `repair_requests`;
DROP TABLE IF EXISTS `repair_orders`;
DROP TABLE IF EXISTS `error_codes`;
DROP TABLE IF EXISTS `common_problems`;
DROP TABLE IF EXISTS `spare_parts`;
DROP TABLE IF EXISTS `part_purchases`;
DROP TABLE IF EXISTS `payments`;
DROP TABLE IF EXISTS `subscriptions`;
DROP TABLE IF EXISTS `sms_logs`;
DROP TABLE IF EXISTS `general_settings`;
DROP TABLE IF EXISTS `settings`;
DROP TABLE IF EXISTS `users`;
SET FOREIGN_KEY_CHECKS = 1;

-- 1. USERS TABLE (Holds Customers, Technicians, and Administrators)
CREATE TABLE `users` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `phone` VARCHAR(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL UNIQUE,
  `password_hash` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` ENUM('client', 'technician', 'admin') NOT NULL DEFAULT 'client',
  `is_super_admin` TINYINT(1) NOT NULL DEFAULT 0,
  `city` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  -- Expanded technician fields (fully integrated)
  `specialty` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL, -- JSON array of specialized categories
  `documents` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL, -- JSON array of verification document URLs
  `rating` DECIMAL(3, 2) NOT NULL DEFAULT 5.00,
  `completed_orders` INT NOT NULL DEFAULT 0,
  `balance` DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  `is_verified` TINYINT(1) NOT NULL DEFAULT 0,
  `active_location` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `avatar_url` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_users_phone` (`phone`),
  INDEX `idx_users_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. ERROR CODES TABLE
CREATE TABLE `error_codes` (
  `id` VARCHAR(100) NOT NULL PRIMARY KEY,
  `code` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `brand` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `causes` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `steps` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `precautions` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `hazard_level` VARCHAR(50) DEFAULT 'medium',
  `hazard_description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `tools_needed` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `related_parts` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `views` INT DEFAULT 0,
  `updated_by` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'system',
  `is_approved` TINYINT(1) DEFAULT 0,
  `is_virtual` TINYINT(1) DEFAULT 0,
  `is_common_problem` TINYINT(1) DEFAULT 0,
  `tags` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `device_type` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `error_code` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `error_title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `compatible_models` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `solutions` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `ai_analysis` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `technician_required` TINYINT(1) DEFAULT 0,
  `video_url` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` VARCHAR(50),
  INDEX `idx_err_code` (`code`),
  INDEX `idx_err_brand` (`brand`),
  INDEX `idx_err_cat` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. COMMON PROBLEMS TABLE
CREATE TABLE `common_problems` (
  `id` VARCHAR(100) NOT NULL PRIMARY KEY,
  `code` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'مشکل شایع',
  `category` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `brand` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `causes` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `steps` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `precautions` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `hazard_level` VARCHAR(50) DEFAULT 'medium',
  `tags` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `video_url` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` VARCHAR(50),
  INDEX `idx_cp_brand` (`brand`),
  INDEX `idx_cp_cat` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. REPAIR ORDERS TABLE
CREATE TABLE `repair_orders` (
  `id` VARCHAR(100) NOT NULL PRIMARY KEY,
  `customer_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_phone` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `city` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `region` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `address` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `category` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `brand` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `model` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `error_code` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `status` VARCHAR(50) DEFAULT 'registered',
  `date` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `time_slot` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `technician_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `technician_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `technician_phone` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `estimated_cost` DECIMAL(12, 2) DEFAULT 0,
  `repair_log` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `parts_used` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `media_urls` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `rating` DECIMAL(3, 2) DEFAULT NULL,
  `review` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  INDEX `idx_ord_cust` (`customer_phone`),
  INDEX `idx_ord_tech` (`technician_id`),
  INDEX `idx_ord_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. SPARE PARTS TABLE
CREATE TABLE `spare_parts` (
  `id` VARCHAR(100) NOT NULL PRIMARY KEY,
  `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `price` DECIMAL(12, 2) NOT NULL DEFAULT 0,
  `image` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `category` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `compatibility` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, -- JSON list
  `stock` INT DEFAULT 0,
  INDEX `idx_part_cat` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. PART PURCHASES TABLE
CREATE TABLE `part_purchases` (
  `id` VARCHAR(100) NOT NULL PRIMARY KEY,
  `part_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `part_name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `part_category` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_phone` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_address` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` DECIMAL(12, 2) NOT NULL DEFAULT 0,
  `date` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` VARCHAR(50) DEFAULT 'pending',
  `quantity` INT DEFAULT 1,
  `card_holder` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `track_number` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `notes` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  INDEX `idx_purchase_part` (`part_id`),
  INDEX `idx_purchase_phone` (`customer_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. SUBSCRIPTIONS TABLE
CREATE TABLE `subscriptions` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `plan_name` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, -- '1_month', '3_month', '6_month', '12_month'
  `start_date` TIMESTAMP NOT NULL,
  `expiry_date` TIMESTAMP NOT NULL,
  `is_active` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  INDEX `idx_subs_user` (`user_id`),
  INDEX `idx_subs_dates` (`expiry_date`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. PAYMENTS TABLE
CREATE TABLE `payments` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `amount` DECIMAL(12, 2) NOT NULL, -- Tomans
  `gateway` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, -- 'zarinpal' or 'bazaar'
  `authority` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL UNIQUE,
  `ref_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL UNIQUE,
  `status` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `plan` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `card_holder` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `completed_at` TIMESTAMP NULL DEFAULT NULL,
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  INDEX `idx_payments_user` (`user_id`),
  INDEX `idx_payments_auth` (`authority`),
  INDEX `idx_payments_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. SMS LOGS TABLE
CREATE TABLE `sms_logs` (
  `id` VARCHAR(100) NOT NULL PRIMARY KEY,
  `phone` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `time` VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_msg` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  INDEX `idx_sms_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. GENERAL SETTINGS TABLE (Config key-value store)
CREATE TABLE `general_settings` (
  `key_name` VARCHAR(100) NOT NULL PRIMARY KEY,
  `value_data` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. REPAIR REQUESTS (Old requests mapping table for backward compatibility)
CREATE TABLE `repair_requests` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `technician_id` INT DEFAULT NULL,
  `city` VARCHAR(50) NOT NULL,
  `appliance` VARCHAR(100) NOT NULL,
  `brand` VARCHAR(100) NOT NULL,
  `model` VARCHAR(100) NOT NULL,
  `problem_description` TEXT NOT NULL,
  `status` VARCHAR(50) NOT NULL DEFAULT 'pending',
  `estimated_price` DECIMAL(12, 2) DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_repairs_user` (`user_id`),
  INDEX `idx_repairs_tech` (`technician_id`),
  INDEX `idx_repairs_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. ACTIVITY LOGS TABLE
CREATE TABLE `activity_logs` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT DEFAULT NULL,
  `activity_type` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `ip_address` VARCHAR(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_logs_type` (`activity_type`),
  INDEX `idx_logs_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 13. SEED DYNAMIC SYSTEM CONFIGURATION
INSERT INTO `general_settings` (`key_name`, `value_data`) VALUES
('adminPassword', '"Abbasi163@#1234"'),
('smsSettings', '{"provider": "simulated", "apiKey": "", "lineNumber": "", "otpPatternCode": "", "statusNotificationPatternCode": "", "enabled": false}'),
('citiesList', '["تهران", "اصفهان", "مشهد", "شیراز", "تبریز", "کرج", "قم", "اهواز"]'),
('brandsList', '["بوتان", "ایران رادیاتور", "الجی", "سامسونگ", "بوش", "دوو", "اسنوا", "جنرال الکتریک"]'),
('categoriesList', '["پکیج", "کولر گازی", "یخچال", "لباسشویی", "ظرفشویی", "مایکروفر"]'),
('modelsList', '["کالدا ونزیا", "پرلا", "اپتیما", "S8", "پرلا پرو", "ورونا"]'),
('adminAnnouncement', '{"text": "به سامانه جامع کدیار۲۴ خوش آمدید. تمامی خدمات با تعرفه مصوب ارائه می‌گردد.", "type": "info", "active": true}'),
('trustBadges', '["ضمانت ۱۸۰ روزه قطعات", "تکنسین‌های مجاز و احراز هویت شده", "پشتیبانی ۲۴ ساعته آنلاین"]'),
('supportPhone', '"09120947304"');

-- Seed standard secure hashed administrator account (using phone '09120947304' and password 'Abbasi163@#1234')
INSERT INTO `users` (`phone`, `password_hash`, `full_name`, `role`, `is_super_admin`)
VALUES ('09120947304', '$2y$10$k1rA9BvE8X97bC8mD7yGeOB9Uv9Kk7X/Y1U7/b6eK76v7X9Y1U7Y1', 'مدیر عالی کدیار24', 'admin', 1)
ON DUPLICATE KEY UPDATE `is_super_admin` = 1;
