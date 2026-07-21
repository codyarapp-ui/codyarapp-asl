<?php
/**
 * ----------------------------------------------------------------------------------
 * IRAN-SERVICE CPANEL CENTRAL RELATIONAL API GATEWAY (PHP & MYSQL)
 * ----------------------------------------------------------------------------------
 * This script serves as the complete, native MySQL database synchronizer for cPanel.
 * It connects to your secure local cPanel MySQL database and stores all data (users,
 * technicians, orders, payments, subscriptions, error codes, spare parts, etc.)
 * in highly organized, categorized, and indexed MySQL relational tables.
 * ----------------------------------------------------------------------------------
 */

// Enable Error Reporting for troubleshooting
ini_set('display_errors', 1);
error_reporting(E_ALL);

// Security Headers & CORS policies
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With");
header("Content-Type: application/json; charset=UTF-8");

// Handle preflight OPTIONS requests gracefully
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// Load .env file if it exists
if (file_exists(__DIR__ . '/.env')) {
    $lines = file(__DIR__ . '/.env', FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
    foreach ($lines as $line) {
        $line = trim($line);
        if (empty($line) || strpos($line, '#') === 0) continue;
        if (strpos($line, '=') !== false) {
            list($name, $value) = explode('=', $line, 2);
            $name = trim($name);
            $value = trim($value);
            if (isset($value[0]) && ($value[0] === '"' || $value[0] === "'")) {
                $quoteChar = $value[0];
                $pos = strpos($value, $quoteChar, 1);
                if ($pos !== false) {
                    $value = substr($value, 1, $pos - 1);
                } else {
                    $value = trim($value, '"\'');
                }
            } else {
                $parts = preg_split('/\s+#/', $value, 2);
                $value = trim($parts[0]);
            }
            putenv(sprintf('%s=%s', $name, $value));
            $_ENV[$name] = $value;
            $_SERVER[$name] = $value;
        }
    }
}

// Database Connection Parameters
define('DB_HOST', isset($_ENV['DB_HOST']) ? $_ENV['DB_HOST'] : (getenv('DB_HOST') ?: 'localhost'));
define('DB_USER', isset($_ENV['DB_USER']) ? $_ENV['DB_USER'] : (getenv('DB_USER') ?: 'cubxrhuv_siteuser'));
define('DB_PASS', isset($_ENV['DB_PASS']) ? $_ENV['DB_PASS'] : (getenv('DB_PASS') ?: 'Abbasi163@#'));
define('DB_NAME', isset($_ENV['DB_NAME']) ? $_ENV['DB_NAME'] : (getenv('DB_NAME') ?: 'cubxrhuv_site.bniaz'));

// Try connecting using multiple database naming options to bypass cPanel prefix issues
$dbNames = array(DB_NAME, 'cubxrhuv_site.bniaz', 'cubxrhuv_site_bniaz', 'cubxrhuv_site');
$pdo = null;
$connectError = '';

foreach ($dbNames as $dbName) {
    try {
        $pdo = new PDO(
            "mysql:host=" . DB_HOST . ";dbname=" . $dbName . ";charset=utf8mb4",
            DB_USER,
            DB_PASS,
            array(
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::MYSQL_ATTR_INIT_COMMAND => "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci"
            )
        );
        if ($pdo) {
            break;
        }
    } catch (PDOException $e) {
        $connectError = $e->getMessage();
    }
}

if (!$pdo) {
    echo json_encode(array(
        "status" => "error",
        "error" => "Database Connection Failed. Last error: " . $connectError
    ));
    exit;
}

// --- AUTOMATIC TABLE AND SCHEMA INITIALIZATION ENGINE ---
try {
    // 1. general_settings
    $pdo->exec("CREATE TABLE IF NOT EXISTS `general_settings` (
      `key_name` VARCHAR(100) NOT NULL PRIMARY KEY,
      `value_data` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    
    // 2. users
    $pdo->exec("CREATE TABLE IF NOT EXISTS `users` (
      `id` INT AUTO_INCREMENT PRIMARY KEY,
      `phone` VARCHAR(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL UNIQUE,
      `password_hash` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `full_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
      `role` ENUM('client', 'technician', 'admin') NOT NULL DEFAULT 'client',
      `is_super_admin` TINYINT(1) NOT NULL DEFAULT 0,
      `city` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
      `specialty` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
      `documents` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // 3. error_codes
    $pdo->exec("CREATE TABLE IF NOT EXISTS `error_codes` (
      `id` VARCHAR(100) NOT NULL PRIMARY KEY,
      `code` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `category` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `brand` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `model` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `causes` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `steps` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `precautions` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `hazard_level` VARCHAR(50) DEFAULT 'medium',
      `hazard_description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `tools_needed` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `related_parts` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `views` INT DEFAULT 0,
      `updated_by` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'system',
      `is_approved` TINYINT(1) DEFAULT 0,
      `is_virtual` TINYINT(1) DEFAULT 0,
      `is_common_problem` TINYINT(1) DEFAULT 0,
      `tags` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `device_type` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `error_code` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `error_title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `compatible_models` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `solutions` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `ai_analysis` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `technician_required` TINYINT(1) DEFAULT 0,
      `video_url` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `created_at` VARCHAR(50),
      INDEX `idx_err_code` (`code`),
      INDEX `idx_err_brand` (`brand`),
      INDEX `idx_err_cat` (`category`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // 4. common_problems
    $pdo->exec("CREATE TABLE IF NOT EXISTS `common_problems` (
      `id` VARCHAR(100) NOT NULL PRIMARY KEY,
      `code` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'مشکل شایع',
      `category` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `brand` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `model` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `causes` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `steps` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `precautions` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `hazard_level` VARCHAR(50) DEFAULT 'medium',
      `tags` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `video_url` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `created_at` VARCHAR(50),
      INDEX `idx_cp_brand` (`brand`),
      INDEX `idx_cp_cat` (`category`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // 5. spare_parts
    $pdo->exec("CREATE TABLE IF NOT EXISTS `spare_parts` (
      `id` VARCHAR(100) NOT NULL PRIMARY KEY,
      `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `price` DECIMAL(12, 2) NOT NULL DEFAULT 0,
      `image` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `category` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `compatibility` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `stock` INT DEFAULT 0,
      INDEX `idx_part_cat` (`category`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // 6. part_purchases (storeOrders)
    $pdo->exec("CREATE TABLE IF NOT EXISTS `part_purchases` (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // 7. repair_orders
    $pdo->exec("CREATE TABLE IF NOT EXISTS `repair_orders` (
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
      `parts_used` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `media_urls` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `rating` DECIMAL(3, 2) DEFAULT NULL,
      `review` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `created_at` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      INDEX `idx_ord_cust` (`customer_phone`),
      INDEX `idx_ord_tech` (`technician_id`),
      INDEX `idx_ord_status` (`status`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // 8. sms_logs
    $pdo->exec("CREATE TABLE IF NOT EXISTS `sms_logs` (
      `id` VARCHAR(100) NOT NULL PRIMARY KEY,
      `phone` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `message` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `time` VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `type` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `status` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `error_msg` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      `created_at` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      INDEX `idx_sms_phone` (`phone`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // 9. Legacy table mappings for backwards compliance (payments & subscriptions & old repair_requests)
    $pdo->exec("CREATE TABLE IF NOT EXISTS `subscriptions` (
      `id` INT AUTO_INCREMENT PRIMARY KEY,
      `user_id` INT NOT NULL,
      `plan_name` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `start_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      `expiry_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      `is_active` TINYINT(1) NOT NULL DEFAULT 1,
      `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS `payments` (
      `id` INT AUTO_INCREMENT PRIMARY KEY,
      `user_id` INT NOT NULL,
      `amount` DECIMAL(12, 2) NOT NULL,
      `gateway` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `authority` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL UNIQUE,
      `ref_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL UNIQUE,
      `status` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
      `plan` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
      `card_holder` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
      `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      `completed_at` TIMESTAMP NULL DEFAULT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $pdo->exec("CREATE TABLE IF NOT EXISTS `repair_requests` (
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
      `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // Seed default administrative account if missing (phone: 09120947304, password: Abbasi163@#1234)
    $check_admin = $pdo->query("SELECT id FROM users WHERE phone='09120947304' LIMIT 1")->fetch();
    if (!$check_admin) {
        $pdo->exec("INSERT INTO `users` (`phone`, `password_hash`, `full_name`, `role`, `is_super_admin`)
        VALUES ('09120947304', '$2y$10\$k1rA9BvE8X97bC8mD7yGeOB9Uv9Kk7X/Y1U7/b6eK76v7X9Y1U7Y1', 'مدیر عالی کدیار24', 'admin', 1)");
    }

} catch (Exception $schemaEx) {
    // Silent fallback
}

// --- CENTRAL SQL DATABASE ABSTRACTION & HELPER FUNCTIONS ---

function saveGeneralSetting($pdo, $key, $val) {
    $json = json_encode($val, JSON_UNESCAPED_UNICODE);
    $stmt = $pdo->prepare("INSERT INTO `general_settings` (`key_name`, `value_data`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `value_data` = ?");
    $stmt->execute(array($key, $json, $json));
}

function getGeneralSetting($pdo, $key, $default = null) {
    try {
        $stmt = $pdo->prepare("SELECT `value_data` FROM `general_settings` WHERE `key_name` = ? LIMIT 1");
        $stmt->execute(array($key));
        $row = $stmt->fetch();
        if ($row) {
            return json_decode($row['value_data'], true);
        }
    } catch (Exception $e) {}
    return $default;
}

function mysqlSyncList($pdo, $tableName, $list, $fieldMappings, $idCol = 'id') {
    if (!is_array($list)) return;
    
    // Collect incoming IDs to delete removed items
    $incomingIds = array();
    foreach ($list as $item) {
        if (isset($item[$idCol])) {
            $incomingIds[] = (string)$item[$idCol];
        }
    }
    
    // Delete removed items
    if (!empty($incomingIds)) {
        $placeholders = implode(',', array_fill(0, count($incomingIds), '?'));
        $stmtDel = $pdo->prepare("DELETE FROM `$tableName` WHERE `$idCol` NOT IN ($placeholders)");
        $stmtDel->execute($incomingIds);
    } else {
        $pdo->exec("DELETE FROM `$tableName`");
    }
    
    // Insert or Update items
    foreach ($list as $item) {
        if (!isset($item[$idCol])) continue;
        
        $cols = array();
        $vals = array();
        $updates = array();
        $params = array();
        
        foreach ($fieldMappings as $jsonKey => $dbCol) {
            $val = null;
            if (array_key_exists($jsonKey, $item)) {
                $val = $item[$jsonKey];
                if (is_array($val)) {
                    $val = json_encode($val, JSON_UNESCAPED_UNICODE);
                } elseif (is_bool($val)) {
                    $val = $val ? 1 : 0;
                }
            }
            
            $cols[] = "`$dbCol`";
            $vals[] = "?";
            $updates[] = "`$dbCol` = VALUES(`$dbCol`)";
            $params[] = $val;
        }
        
        // Add ID column
        $cols[] = "`$idCol`";
        $vals[] = "?";
        $params[] = (string)$item[$idCol];
        
        $sql = "INSERT INTO `$tableName` (" . implode(', ', $cols) . ") VALUES (" . implode(', ', $vals) . ") 
                ON DUPLICATE KEY UPDATE " . implode(', ', $updates);
        
        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);
    }
}

// Helper to fetch the dynamically unified central state merged from MySQL relation tables
function getCentralState($pdo) {
    try {
        $state = array();
        
        // 1. Load configuration lists and settings parameters
        $defaultLookups = array(
            'citiesList' => array("تهران", "اصفهان", "مشهد", "شیراز", "تبریز", "کرج", "قم", "اهواز"),
            'brandsList' => array("بوتان", "ایران رادیاتور", "الجی", "سامسونگ", "بوش", "دوو", "اسنوا", "جنرال الکتریک"),
            'categoriesList' => array("پکیج", "کولر گازی", "یخچال", "لباسشویی", "ظرفشویی", "مایکروفر"),
            'modelsList' => array("کالدا ونزیا", "پرلا", "اپتیما", "S8", "پرلا پرو", "ورونا"),
            'adminAnnouncement' => array("text" => "به سامانه جامع کدیار۲۴ خوش آمدید. تمامی خدمات با تعرفه مصوب ارائه می‌گردد.", "type" => "info", "active" => true),
            'trustBadges' => array("ضمانت ۱۸۰ روزه قطعات", "تکنسین‌های مجاز و احراز هویت شده", "پشتیبانی ۲۴ ساعته آنلاین"),
            'supportPhone' => "09120947304",
            'adminPassword' => "Abbasi163@#1234",
            'smsSettings' => array("provider" => "simulated", "apiKey" => "", "lineNumber" => "", "otpPatternCode" => "", "statusNotificationPatternCode" => "", "enabled" => false)
        );
        
        foreach ($defaultLookups as $k => $defaultVal) {
            $state[$k] = getGeneralSetting($pdo, $k, $defaultVal);
        }
        
        // Load legacy / arbitrary extra keys
        $legacy = getGeneralSetting($pdo, 'legacy_json_state', array());
        if (is_array($legacy)) {
            foreach ($legacy as $k => $val) {
                if (!isset($state[$k])) {
                    $state[$k] = $val;
                }
            }
        }
        
        // 2. Load users
        $usersStmt = $pdo->query("SELECT id, phone, full_name, role, is_super_admin, city, created_at FROM users ORDER BY id ASC");
        $mappedUsers = array();
        if ($usersStmt) {
            $mysqlUsers = $usersStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlUsers as $u) {
                $mappedUsers[] = array(
                    "id" => strval($u['id']),
                    "phone" => $u['phone'],
                    "full_name" => $u['full_name'] ? $u['full_name'] : 'کاربر گرامی',
                    "role" => $u['role'],
                    "is_super_admin" => intval($u['is_super_admin']) === 1,
                    "city" => $u['city'],
                    "created_at" => $u['created_at']
                );
            }
        }
        $state['users'] = $mappedUsers;
        
        // 3. Load technicians from users
        $techsStmt = $pdo->query("SELECT * FROM users WHERE role = 'technician' ORDER BY id ASC");
        $mappedTechs = array();
        if ($techsStmt) {
            $mysqlTechs = $techsStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlTechs as $u) {
                $specs = json_decode($u['specialty'] ?? '', true);
                if (!is_array($specs)) $specs = array();
                $docs = json_decode($u['documents'] ?? '', true);
                if (!is_array($docs)) $docs = array();
                
                $mappedTechs[] = array(
                    "id" => "tech_" . $u['id'],
                    "name" => $u['full_name'] ? $u['full_name'] : 'تکنسین گرامی',
                    "phone" => $u['phone'],
                    "password" => "",
                    "specialty" => $specs,
                    "rating" => isset($u['rating']) ? floatval($u['rating']) : 5.0,
                    "completedOrders" => isset($u['completed_orders']) ? intval($u['completed_orders']) : 0,
                    "balance" => isset($u['balance']) ? floatval($u['balance']) : 0.0,
                    "isVerified" => !empty($u['is_verified']) ? true : false,
                    "city" => $u['city'] ? $u['city'] : "نامشخص",
                    "activeLocation" => $u['active_location'] ? $u['active_location'] : "نامشخص",
                    "documents" => $docs,
                    "avatarUrl" => $u['avatar_url'] ? $u['avatar_url'] : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=60"
                );
            }
        }
        $state['technicians'] = $mappedTechs;
        
        // 4. Load errorCodes
        $errsStmt = $pdo->query("SELECT * FROM error_codes ORDER BY id ASC");
        $mappedErrs = array();
        if ($errsStmt) {
            $mysqlErrs = $errsStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlErrs as $row) {
                $mappedErrs[] = array(
                    "id" => $row['id'],
                    "code" => $row['code'],
                    "category" => $row['category'],
                    "brand" => $row['brand'],
                    "model" => $row['model'],
                    "title" => $row['title'],
                    "description" => $row['description'],
                    "causes" => json_decode($row['causes'] ?? '', true) ?: array(),
                    "steps" => json_decode($row['steps'] ?? '', true) ?: array(),
                    "precautions" => json_decode($row['precautions'] ?? '', true) ?: array(),
                    "hazardLevel" => $row['hazard_level'],
                    "hazardDescription" => $row['hazard_description'],
                    "toolsNeeded" => json_decode($row['tools_needed'] ?? '', true) ?: array(),
                    "relatedParts" => json_decode($row['related_parts'] ?? '', true) ?: array(),
                    "views" => intval($row['views']),
                    "updatedBy" => $row['updated_by'],
                    "isApproved" => intval($row['is_approved']) === 1,
                    "isVirtual" => intval($row['is_virtual']) === 1,
                    "isCommonProblem" => intval($row['is_common_problem']) === 1,
                    "tags" => json_decode($row['tags'] ?? '', true) ?: array(),
                    "device_type" => $row['device_type'],
                    "error_code" => $row['error_code'],
                    "error_title" => $row['error_title'],
                    "compatible_models" => json_decode($row['compatible_models'] ?? '', true) ?: array(),
                    "solutions" => json_decode($row['solutions'] ?? '', true) ?: array(),
                    "ai_analysis" => $row['ai_analysis'],
                    "technician_required" => intval($row['technician_required']) === 1,
                    "video_url" => $row['video_url'],
                    "created_at" => $row['created_at']
                );
            }
        }
        $state['errorCodes'] = $mappedErrs;
        
        // 5. Load commonProblems
        $cpStmt = $pdo->query("SELECT * FROM common_problems ORDER BY id ASC");
        $mappedCps = array();
        if ($cpStmt) {
            $mysqlCps = $cpStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlCps as $row) {
                $mappedCps[] = array(
                    "id" => $row['id'],
                    "code" => $row['code'],
                    "category" => $row['category'],
                    "brand" => $row['brand'],
                    "model" => $row['model'],
                    "title" => $row['title'],
                    "description" => $row['description'],
                    "causes" => json_decode($row['causes'] ?? '', true) ?: array(),
                    "steps" => json_decode($row['steps'] ?? '', true) ?: array(),
                    "precautions" => json_decode($row['precautions'] ?? '', true) ?: array(),
                    "hazardLevel" => $row['hazard_level'],
                    "tags" => json_decode($row['tags'] ?? '', true) ?: array(),
                    "video_url" => $row['video_url'],
                    "created_at" => $row['created_at']
                );
            }
        }
        $state['commonProblems'] = $mappedCps;
        
        // 6. Load spareParts
        $spStmt = $pdo->query("SELECT * FROM spare_parts ORDER BY id ASC");
        $mappedSps = array();
        if ($spStmt) {
            $mysqlSps = $spStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlSps as $row) {
                $mappedSps[] = array(
                    "id" => $row['id'],
                    "name" => $row['name'],
                    "description" => $row['description'],
                    "price" => floatval($row['price']),
                    "image" => $row['image'],
                    "category" => $row['category'],
                    "compatibility" => json_decode($row['compatibility'] ?? '', true) ?: array(),
                    "stock" => intval($row['stock'])
                );
            }
        }
        $state['spareParts'] = $mappedSps;
        
        // 7. Load partPurchases (storeOrders)
        $ppStmt = $pdo->query("SELECT * FROM part_purchases ORDER BY id DESC");
        $mappedPps = array();
        if ($ppStmt) {
            $mysqlPps = $ppStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlPps as $row) {
                $mappedPps[] = array(
                    "id" => $row['id'],
                    "partId" => $row['part_id'],
                    "partName" => $row['part_name'],
                    "partCategory" => $row['part_category'],
                    "customerName" => $row['customer_name'],
                    "customerPhone" => $row['customer_phone'],
                    "customerAddress" => $row['customer_address'],
                    "price" => floatval($row['price']),
                    "date" => $row['date'],
                    "status" => $row['status'],
                    "quantity" => intval($row['quantity']),
                    "cardHolder" => $row['card_holder'],
                    "trackNumber" => $row['track_number'],
                    "notes" => $row['notes'],
                    "created_at" => $row['created_at']
                );
            }
        }
        $state['partPurchases'] = $mappedPps;
        $state['storeOrders'] = $mappedPps;
        
        // 8. Load orders & repairRequests
        $ordersStmt = $pdo->query("SELECT * FROM repair_orders ORDER BY id DESC");
        $mappedOrders = array();
        if ($ordersStmt) {
            $mysqlOrders = $ordersStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlOrders as $row) {
                $mappedOrders[] = array(
                    "id" => $row['id'],
                    "customerName" => $row['customer_name'],
                    "customerPhone" => $row['customer_phone'],
                    "city" => $row['city'],
                    "region" => $row['region'],
                    "address" => $row['address'],
                    "category" => $row['category'],
                    "brand" => $row['brand'],
                    "model" => $row['model'],
                    "errorCode" => $row['error_code'],
                    "description" => $row['description'],
                    "status" => $row['status'],
                    "date" => $row['date'],
                    "timeSlot" => $row['time_slot'],
                    "technicianId" => $row['technician_id'],
                    "technicianName" => $row['technician_name'],
                    "technicianPhone" => $row['technician_phone'],
                    "estimatedCost" => floatval($row['estimated_cost']),
                    "repairLog" => $row['repair_log'],
                    "partsUsed" => json_decode($row['parts_used'] ?? '', true) ?: array(),
                    "mediaUrls" => json_decode($row['media_urls'] ?? '', true) ?: array(),
                    "rating" => isset($row['rating']) ? floatval($row['rating']) : null,
                    "review" => $row['review'],
                    "createdAt" => $row['created_at']
                );
            }
        }
        
        // Fetch legacy repair_requests table for full compliance
        $reqsStmt = $pdo->query("SELECT r.*, u.phone as uphone, u.full_name as uname FROM repair_requests r LEFT JOIN users u ON r.user_id = u.id ORDER BY r.id DESC");
        if ($reqsStmt) {
            $mysqlReqs = $reqsStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlReqs as $r) {
                $statusMap = array(
                    'pending' => 'waiting',
                    'assigned' => 'accepted',
                    'in_progress' => 'repairing',
                    'completed' => 'completed',
                    'cancelled' => 'cancelled'
                );
                $status = isset($statusMap[$r['status']]) ? $statusMap[$r['status']] : 'waiting';
                $legacyId = "rep_" . $r['id'];
                
                $alreadyExists = false;
                foreach ($mappedOrders as $mo) {
                    if ($mo['id'] === $legacyId) {
                        $alreadyExists = true;
                        break;
                    }
                }
                
                if (!$alreadyExists) {
                    $mappedOrders[] = array(
                        "id" => $legacyId,
                        "customerName" => $r['uname'] ? $r['uname'] : 'مشتری گرامی',
                        "customerPhone" => $r['uphone'] ? $r['uphone'] : '',
                        "city" => $r['city'],
                        "region" => "عمومی",
                        "address" => "نیاز به هماهنگی تلفنی",
                        "category" => $r['appliance'],
                        "brand" => $r['brand'],
                        "model" => $r['model'] ? $r['model'] : 'عمومی',
                        "errorCode" => "ثبت شده آنلاین",
                        "description" => $r['problem_description'],
                        "status" => $status,
                        "date" => explode(' ', $r['created_at'])[0],
                        "timeSlot" => "هماهنگی بعدی",
                        "technicianId" => $r['technician_id'] ? "tech_" . $r['technician_id'] : "",
                        "technicianName" => "",
                        "technicianPhone" => "",
                        "estimatedCost" => floatval($r['estimated_price'] ?? 0),
                        "repairLog" => "",
                        "partsUsed" => array(),
                        "mediaUrls" => array(),
                        "rating" => null,
                        "review" => "",
                        "createdAt" => $r['created_at']
                    );
                }
            }
        }
        
        $state['orders'] = $mappedOrders;
        $state['repairRequests'] = $mappedOrders;
        
        // 9. Load smsLogs
        $smsStmt = $pdo->query("SELECT * FROM sms_logs ORDER BY id DESC LIMIT 500");
        $mappedSms = array();
        if ($smsStmt) {
            $mysqlSms = $smsStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlSms as $row) {
                $mappedSms[] = array(
                    "id" => $row['id'],
                    "phone" => $row['phone'],
                    "message" => $row['message'],
                    "time" => $row['time'],
                    "type" => $row['type'],
                    "status" => $row['status'],
                    "error" => $row['error_msg']
                );
            }
        }
        $state['smsLogs'] = $mappedSms;
        
        // 10. Load payments
        $paysStmt = $pdo->query("SELECT p.*, u.phone as uphone, u.full_name as uname FROM payments p LEFT JOIN users u ON p.user_id = u.id ORDER BY p.id DESC");
        $mappedPays = array();
        if ($paysStmt) {
            $mysqlPays = $paysStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlPays as $row) {
                $mappedPays[] = array(
                    "id" => strval($row['id']),
                    "user_id" => $row['uphone'] ? $row['uphone'] : "کاربر " . $row['user_id'],
                    "amount" => floatval($row['amount']),
                    "gateway" => $row['gateway'],
                    "authority" => $row['authority'],
                    "ref_id" => $row['ref_id'],
                    "status" => $row['status'],
                    "plan" => $row['plan'],
                    "card_holder" => $row['card_holder'],
                    "created_at" => strval($row['created_at']),
                    "completed_at" => strval($row['completed_at'])
                );
            }
        }
        $state['payments'] = $mappedPays;
        
        // 11. Load subscriptions
        $subsStmt = $pdo->query("SELECT s.*, u.phone as uphone FROM subscriptions s LEFT JOIN users u ON s.user_id = u.id ORDER BY s.id DESC");
        $mappedSubs = array();
        if ($subsStmt) {
            $mysqlSubs = $subsStmt->fetchAll(PDO::FETCH_ASSOC);
            foreach ($mysqlSubs as $row) {
                $mappedSubs[] = array(
                    "id" => strval($row['id']),
                    "user_id" => $row['uphone'] ? $row['uphone'] : "کاربر " . $row['user_id'],
                    "plan_name" => $row['plan_name'],
                    "start_date" => strval($row['start_date']),
                    "expiry_date" => strval($row['expiry_date']),
                    "is_active" => intval($row['is_active']) === 1,
                    "created_at" => strval($row['created_at'])
                );
            }
        }
        $state['subscriptions'] = $mappedSubs;
        
        return $state;
    } catch (Exception $e) {
        return array('error' => $e->getMessage());
    }
}

// Helper to save unified state directly into categorized tables
function saveCentralState($pdo, $data) {
    try {
        $errorCodeMappings = array(
            'code' => 'code',
            'category' => 'category',
            'brand' => 'brand',
            'model' => 'model',
            'title' => 'title',
            'description' => 'description',
            'causes' => 'causes',
            'steps' => 'steps',
            'precautions' => 'precautions',
            'hazardLevel' => 'hazard_level',
            'hazardDescription' => 'hazard_description',
            'toolsNeeded' => 'tools_needed',
            'relatedParts' => 'related_parts',
            'views' => 'views',
            'updatedBy' => 'updated_by',
            'isApproved' => 'is_approved',
            'isVirtual' => 'is_virtual',
            'isCommonProblem' => 'is_common_problem',
            'tags' => 'tags',
            'device_type' => 'device_type',
            'error_code' => 'error_code',
            'error_title' => 'error_title',
            'compatible_models' => 'compatible_models',
            'solutions' => 'solutions',
            'ai_analysis' => 'ai_analysis',
            'technician_required' => 'technician_required',
            'video_url' => 'video_url',
            'created_at' => 'created_at'
        );

        $commonProblemMappings = array(
            'code' => 'code',
            'category' => 'category',
            'brand' => 'brand',
            'model' => 'model',
            'title' => 'title',
            'description' => 'description',
            'causes' => 'causes',
            'steps' => 'steps',
            'precautions' => 'precautions',
            'hazardLevel' => 'hazard_level',
            'tags' => 'tags',
            'video_url' => 'video_url',
            'created_at' => 'created_at'
        );

        $sparePartMappings = array(
            'name' => 'name',
            'description' => 'description',
            'price' => 'price',
            'image' => 'image',
            'category' => 'category',
            'compatibility' => 'compatibility',
            'stock' => 'stock'
        );

        $partPurchaseMappings = array(
            'partId' => 'part_id',
            'partName' => 'part_name',
            'partCategory' => 'part_category',
            'customerName' => 'customer_name',
            'customerPhone' => 'customer_phone',
            'customerAddress' => 'customer_address',
            'price' => 'price',
            'date' => 'date',
            'status' => 'status',
            'quantity' => 'quantity',
            'cardHolder' => 'card_holder',
            'trackNumber' => 'track_number',
            'notes' => 'notes',
            'created_at' => 'created_at'
        );

        $repairOrderMappings = array(
            'customerName' => 'customer_name',
            'customerPhone' => 'customer_phone',
            'city' => 'city',
            'region' => 'region',
            'address' => 'address',
            'category' => 'category',
            'brand' => 'brand',
            'model' => 'model',
            'errorCode' => 'error_code',
            'description' => 'description',
            'status' => 'status',
            'date' => 'date',
            'timeSlot' => 'time_slot',
            'technicianId' => 'technician_id',
            'technicianName' => 'technician_name',
            'technicianPhone' => 'technician_phone',
            'estimatedCost' => 'estimated_cost',
            'repairLog' => 'repair_log',
            'partsUsed' => 'parts_used',
            'mediaUrls' => 'media_urls',
            'rating' => 'rating',
            'review' => 'review',
            'createdAt' => 'created_at'
        );

        $smsLogMappings = array(
            'phone' => 'phone',
            'message' => 'message',
            'time' => 'time',
            'type' => 'type',
            'status' => 'status',
            'error' => 'error_msg',
            'created_at' => 'created_at'
        );

        // 1. Sync lookup lists and settings parameters
        $settingKeys = array('citiesList', 'brandsList', 'categoriesList', 'modelsList', 'adminPassword', 'smsSettings', 'adminAnnouncement', 'trustBadges', 'supportPhone');
        foreach ($settingKeys as $k) {
            if (array_key_exists($k, $data)) {
                saveGeneralSetting($pdo, $k, $data[$k]);
            }
        }

        // 2. Relational Synchronization (Syncing individual tables)
        if (array_key_exists('errorCodes', $data)) {
            mysqlSyncList($pdo, 'error_codes', $data['errorCodes'], $errorCodeMappings, 'id');
        }
        if (array_key_exists('commonProblems', $data)) {
            mysqlSyncList($pdo, 'common_problems', $data['commonProblems'], $commonProblemMappings, 'id');
        }
        if (array_key_exists('spareParts', $data)) {
            mysqlSyncList($pdo, 'spare_parts', $data['spareParts'], $sparePartMappings, 'id');
        }
        if (array_key_exists('partPurchases', $data)) {
            mysqlSyncList($pdo, 'part_purchases', $data['partPurchases'], $partPurchaseMappings, 'id');
        }
        if (array_key_exists('storeOrders', $data)) {
            mysqlSyncList($pdo, 'part_purchases', $data['storeOrders'], $partPurchaseMappings, 'id');
        }
        if (array_key_exists('orders', $data)) {
            mysqlSyncList($pdo, 'repair_orders', $data['orders'], $repairOrderMappings, 'id');
        }
        if (array_key_exists('repairRequests', $data)) {
            mysqlSyncList($pdo, 'repair_orders', $data['repairRequests'], $repairOrderMappings, 'id');
        }
        if (array_key_exists('smsLogs', $data)) {
            mysqlSyncList($pdo, 'sms_logs', $data['smsLogs'], $smsLogMappings, 'id');
        }

        // 3. Sync technicians list to the SQL users table
        if (array_key_exists('technicians', $data) && is_array($data['technicians'])) {
            $incomingPhones = array();
            foreach ($data['technicians'] as $tech) {
                if (empty($tech['phone'])) continue;
                $techPhone = preg_replace('/[^0-9]/', '', $tech['phone']);
                $incomingPhones[] = $techPhone;

                $specs = json_encode($tech['specialty'] ?? array(), JSON_UNESCAPED_UNICODE);
                $docs = json_encode($tech['documents'] ?? array(), JSON_UNESCAPED_UNICODE);
                $techName = $tech['name'] ?? 'تکنسین گرامی';
                $isVerifiedVal = !empty($tech['isVerified']) ? 1 : 0;
                $ratingVal = isset($tech['rating']) ? floatval($tech['rating']) : 5.00;
                $completedVal = isset($tech['completedOrders']) ? intval($tech['completedOrders']) : 0;
                $balanceVal = isset($tech['balance']) ? floatval($tech['balance']) : 0.00;
                $locVal = $tech['activeLocation'] ?? ($tech['city'] ?? null);
                $avatarVal = $tech['avatarUrl'] ?? null;

                $checkStmt = $pdo->prepare("SELECT id FROM users WHERE phone = ? LIMIT 1");
                $checkStmt->execute(array($techPhone));
                $dbTech = $checkStmt->fetch();

                if ($dbTech) {
                    $updStmt = $pdo->prepare("UPDATE users SET full_name = ?, role = 'technician', specialty = ?, documents = ?, is_verified = ?, rating = ?, completed_orders = ?, balance = ?, active_location = ?, avatar_url = ? WHERE id = ?");
                    $updStmt->execute(array($techName, $specs, $docs, $isVerifiedVal, $ratingVal, $completedVal, $balanceVal, $locVal, $avatarVal, $dbTech['id']));
                } else {
                    $insStmt = $pdo->prepare("INSERT INTO users (phone, password_hash, full_name, role, specialty, documents, is_verified, rating, completed_orders, balance, active_location, avatar_url) VALUES (?, ?, ?, 'technician', ?, ?, ?, ?, ?, ?, ?, ?)");
                    $insStmt->execute(array($techPhone, password_hash('Abbasi163@#', PASSWORD_BCRYPT), $techName, $specs, $docs, $isVerifiedVal, $ratingVal, $completedVal, $balanceVal, $locVal, $avatarVal));
                }
            }

            // Clean removed technicians
            if (!empty($incomingPhones)) {
                $placeholders = implode(',', array_fill(0, count($incomingPhones), '?'));
                $stmtDelTechs = $pdo->prepare("DELETE FROM users WHERE role = 'technician' AND phone NOT IN ($placeholders)");
                $stmtDelTechs->execute($incomingPhones);
            } else {
                $pdo->exec("DELETE FROM users WHERE role = 'technician'");
            }
        }

        // 4. Save arbitrary/legacy fields inside general_settings
        $cleanData = $data;
        $systemKeys = array('errorCodes', 'commonProblems', 'spareParts', 'partPurchases', 'storeOrders', 'orders', 'repairRequests', 'smsLogs', 'technicians', 'users', 'payments', 'subscriptions');
        $allKnownKeys = array_merge($systemKeys, $settingKeys);
        
        foreach ($allKnownKeys as $k) {
            unset($cleanData[$k]);
        }
        
        if (!empty($cleanData)) {
            $prevLegacy = getGeneralSetting($pdo, 'legacy_json_state', array());
            $mergedLegacy = array_merge($prevLegacy, $cleanData);
            saveGeneralSetting($pdo, 'legacy_json_state', $mergedLegacy);
        }

        return true;
    } catch (Exception $e) {
        return false;
    }
}


// Route Routing Determination
$uri = $_SERVER['REQUEST_URI'];
$method = $_SERVER['REQUEST_METHOD'];

$parsedUrl = parse_url($uri);
$path = $parsedUrl['path'];

// ------------------------------------------------------------------
// ENDPOINT 1: GET DATABASE [ /api/get-database ]
// ------------------------------------------------------------------
if (strpos($path, 'get-database') !== false) {
    $state = getCentralState($pdo);
    echo json_encode($state, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
    exit;
}

// ------------------------------------------------------------------
// ENDPOINT 2: SAVE DATABASE [ /api/save-database ]
// ------------------------------------------------------------------
if (strpos($path, 'save-database') !== false && $method === 'POST') {
    $rawInput = file_get_contents('php://input');
    $payload = json_decode($rawInput, true);
    
    if (!$payload || !is_array($payload)) {
        http_response_code(400);
        echo json_encode(array("status" => "error", "error" => "داده‌های دریافتی نامعتبر است."));
        exit;
    }
    
    $currentState = getCentralState($pdo);
    
    // Merge keys recursively / carefully
    $mergeableKeys = array('orders', 'technicians', 'errorCodes', 'spareParts', 'partPurchases', 'smsLogs');
    foreach ($mergeableKeys as $key) {
        if (isset($payload[$key]) && is_array($payload[$key])) {
            $currentList = isset($currentState[$key]) && is_array($currentState[$key]) ? $currentState[$key] : array();
            $reqList = $payload[$key];
            
            if (empty($reqList)) {
                $emptyCount = 0;
                foreach ($mergeableKeys as $k) {
                    if (isset($payload[$k]) && empty($payload[$k])) {
                        $emptyCount++;
                    }
                }
                if ($emptyCount >= 3) {
                    $currentState[$key] = array();
                } else {
                    $currentState[$key] = array();
                }
            } else {
                $mergedList = array();
                foreach ($currentList as $item) {
                    if (is_array($item) && isset($item['id'])) {
                        $mergedList[$item['id']] = $item;
                    }
                }
                foreach ($reqList as $item) {
                    if (is_array($item) && isset($item['id'])) {
                        $mergedList[$item['id']] = $item;
                    }
                }
                $currentState[$key] = array_values($mergedList);
            }
            unset($payload[$key]);
        }
    }
    
    foreach ($payload as $key => $val) {
        $currentState[$key] = $val;
    }
    
    $success = saveCentralState($pdo, $currentState);
    
    if ($success) {
        echo json_encode(array("status" => "ok", "message" => "دیتابیس با موفقیت روی سرور ملی همگام‌سازی شد."));
    } else {
        http_response_code(500);
        echo json_encode(array("status" => "error", "error" => "خطا در بروزرسانی فیلدهای متناظر دیتابیس"));
    }
    exit;
}

// ------------------------------------------------------------------
// ENDPOINT 3: REAL OR SIMULATED SMS DISPATCH [ /api/send-sms ]
// ------------------------------------------------------------------
if (strpos($path, 'send-sms') !== false && $method === 'POST') {
    $rawInput = file_get_contents('php://input');
    $payload = json_decode($rawInput, true);
    
    $rawPhone = isset($payload['phone']) ? trim($payload['phone']) : '';
    $message = isset($payload['message']) ? trim($payload['message']) : '';
    $templateVars = isset($payload['templateVars']) ? $payload['templateVars'] : null;
    $type = isset($payload['type']) ? trim($payload['type']) : 'status';
    
    if (empty($rawPhone) || empty($message)) {
        http_response_code(400);
        echo json_encode(array("status" => "error", "error" => "گیرنده یا متن پیام وارد نشده است."));
        exit;
    }

    $farsiDigits = array('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹');
    $arabicDigits = array('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩');
    $engDigits = range(0, 9);
    $phone = str_replace($farsiDigits, $engDigits, $rawPhone);
    $phone = str_replace($arabicDigits, $engDigits, $phone);
    $phone = trim($phone);

    if (!preg_match('/^09\d{9}$/', $phone)) {
        http_response_code(400);
        echo json_encode(array("status" => "error", "error" => "فرمت شماره همراه نامعتبر است. شماره همراه باید با 09 شروع شود.")), JSON_UNESCAPED_UNICODE;
        exit;
    }
    
    $currentState = getCentralState($pdo);
    $settings = isset($currentState['smsSettings']) ? $currentState['smsSettings'] : array(
        'provider' => 'simulated',
        'apiKey' => '',
        'lineNumber' => '',
        'otpPatternCode' => '',
        'statusNotificationPatternCode' => '',
        'enabled' => false
    );

    $dispatchStatus = 'sent_simulated';
    $errorMessage = '';

    if (!empty($settings['enabled']) && $settings['provider'] !== 'simulated' && !empty($settings['apiKey'])) {
        try {
            if ($settings['provider'] === 'farazsms') {
                $patternCode = ($type === 'otp') ? $settings['otpPatternCode'] : $settings['statusNotificationPatternCode'];
                $bodyPayload = array(
                    'code' => !empty($patternCode) ? $patternCode : 'DEFAULT_PATTERN',
                    'sender' => !empty($settings['lineNumber']) ? $settings['lineNumber'] : '3000505',
                    'recipient' => $phone,
                    'variable_values' => $templateVars ? $templateVars : array('code' => $message)
                );

                $ch = curl_init("https://api2.ippanel.com/api/v1/sms/pattern/normal/send");
                curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
                curl_setopt($ch, CURLOPT_POST, true);
                curl_setopt($ch, CURLOPT_HTTPHEADER, array(
                    'Authorization: AccessKey ' . $settings['apiKey'],
                    'Content-Type: application/json'
                ));
                curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($bodyPayload));
                curl_setopt($ch, CURLOPT_TIMEOUT, 10);
                
                $apiResponse = curl_exec($ch);
                $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
                curl_close($ch);

                if ($httpCode >= 200 && $httpCode < 300) {
                    $dispatchStatus = 'sent_real_farazsms';
                } else {
                    throw new Exception("FarazSMS IPPanel Gateway Error Status: " . $httpCode);
                }
            } else if ($settings['provider'] === 'kavenegar') {
                $patternCode = ($type === 'otp') ? $settings['otpPatternCode'] : $settings['statusNotificationPatternCode'];
                $tokenValue = ($templateVars && is_array($templateVars)) ? array_values($templateVars)[0] : $message;

                $queryParams = http_build_query(array(
                    'receptor' => $phone,
                    'token' => $tokenValue,
                    'template' => !empty($patternCode) ? $patternCode : 'DEFAULT_TEMPLATE'
                ));

                $url = "https://api.kavenegar.com/v1/" . $settings['apiKey'] . "/verify/lookup.json?" . $queryParams;

                $ch = curl_init($url);
                curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
                curl_setopt($ch, CURLOPT_TIMEOUT, 10);
                
                $apiResponse = curl_exec($ch);
                $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
                curl_close($ch);

                if ($httpCode >= 200 && $httpCode < 300) {
                    $dispatchStatus = 'sent_real_kavenegar';
                } else {
                    throw new Exception("Kavenegar Gateway Error Status: " . $httpCode);
                }
            } else if ($settings['provider'] === 'smsir') {
                $patternCode = ($type === 'otp') ? $settings['otpPatternCode'] : $settings['statusNotificationPatternCode'];
                $parameters = array();
                if ($templateVars && is_array($templateVars)) {
                    foreach ($templateVars as $key => $val) {
                        $parameters[] = array(
                            'name' => strval($key),
                            'value' => strval($val)
                        );
                    }
                } else {
                    $parameters[] = array(
                        'name' => 'code',
                        'value' => strval($message)
                    );
                }

                $bodyPayload = array(
                    'mobile' => $phone,
                    'templateId' => intval($patternCode),
                    'parameters' => $parameters
                );

                $ch = curl_init("https://api.sms.ir/v1/send/verify");
                curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
                curl_setopt($ch, CURLOPT_POST, true);
                curl_setopt($ch, CURLOPT_HTTPHEADER, array(
                    'X-API-KEY: ' . $settings['apiKey'],
                    'Accept: text/plain',
                    'Content-Type: application/json'
                ));
                curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($bodyPayload));
                curl_setopt($ch, CURLOPT_TIMEOUT, 10);

                $apiResponse = curl_exec($ch);
                $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
                curl_close($ch);

                if ($httpCode >= 200 && $httpCode < 300) {
                    $dispatchStatus = 'sent_real_smsir';
                } else {
                    throw new Exception("SMS.ir Gateway Error Status: " . $httpCode);
                }
            }
        } catch (Exception $e) {
            $dispatchStatus = 'failed_with_fallback';
            $errorMessage = $e->getMessage();
        }
    }
    
    date_default_timezone_set('Asia/Tehran');
    $timeStr = date('H:i');
    $logId = 'sms_' . round(microtime(true) * 1000);
    $smsLog = array(
        "id" => $logId,
        "phone" => $phone,
        "message" => $message,
        "time" => $timeStr,
        "type" => $type,
        "status" => $dispatchStatus,
        "error" => $errorMessage ? $errorMessage : null
    );
    
    if (!isset($currentState['smsLogs']) || !is_array($currentState['smsLogs'])) {
        $currentState['smsLogs'] = array();
    }
    array_unshift($currentState['smsLogs'], $smsLog);
    $currentState['smsLogs'] = array_slice($currentState['smsLogs'], 0, 500);
    
    saveCentralState($pdo, $currentState);
    
    echo json_encode(array(
        "status" => "ok",
        "message" => $errorMessage ? "ارسال با شکست مواجه شد و به شبیه‌ساز منتقل گشت." : "پیامک با موفقیت ارسال شد و در سرور ثبت گردید.",
        "log" => $smsLog
    ), JSON_UNESCAPED_UNICODE);
    exit;
}

// Fallback response for unmatched endpoints
http_response_code(404);
echo json_encode(array("status" => "error", "error" => "آدرس درخواست شده معتبر نمی‌باشد."));
exit;
