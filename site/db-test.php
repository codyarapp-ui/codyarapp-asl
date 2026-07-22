<?php
/**
 * ----------------------------------------------------------------------------------
 * IRAN-SERVICE DATABASE CONNECTION DIAGNOSTIC TOOL
 * ----------------------------------------------------------------------------------
 * Upload this file to your cPanel directory (next to api.php) and open it in your browser:
 * https://yourdomain.com/db-test.php
 * ----------------------------------------------------------------------------------
 */

ini_set('display_errors', 1);
error_reporting(E_ALL);

header("Content-Type: text/html; charset=UTF-8");
?>
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <title>عیب‌یابی اتصال دیتابیس سی‌پنل (cPanel DB Diagnostic)</title>
    <style>
        body {
            font-family: Tahoma, Geneva, sans-serif;
            background-color: #f3f4f6;
            color: #1f2937;
            padding: 40px 20px;
            line-height: 1.6;
            font-size: 14px;
        }
        .container {
            max-width: 800px;
            margin: 0 auto;
            background-color: #ffffff;
            border-radius: 12px;
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
            padding: 30px;
        }
        h1 {
            color: #111827;
            border-bottom: 2px solid #e5e7eb;
            padding-bottom: 15px;
            margin-top: 0;
            font-size: 20px;
        }
        .section {
            background-color: #f9fafb;
            border-radius: 8px;
            border: 1px solid #e5e7eb;
            padding: 20px;
            margin-bottom: 20px;
        }
        .section-title {
            font-weight: bold;
            font-size: 16px;
            margin-bottom: 10px;
            display: flex;
            align-items: center;
        }
        .status-success {
            background-color: #ecfdf5;
            border-color: #10b981;
            color: #065f46;
        }
        .status-danger {
            background-color: #fef2f2;
            border-color: #f87171;
            color: #991b1b;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 10px;
        }
        th, td {
            padding: 10px;
            text-align: right;
            border-bottom: 1px solid #e5e7eb;
        }
        th {
            font-weight: bold;
            color: #4b5563;
            width: 30%;
        }
        .guide-box {
            background-color: #eff6ff;
            border-right: 4px solid #3b82f6;
            padding: 15px;
            border-radius: 4px;
            margin-top: 15px;
        }
        .guide-box ol {
            margin: 10px 0 0 0;
            padding-right: 20px;
        }
        .guide-box li {
            margin-bottom: 8px;
        }
        .code {
            font-family: monospace;
            background-color: #f3f4f6;
            padding: 2px 6px;
            border-radius: 4px;
            direction: ltr;
            display: inline-block;
        }
    </style>
</head>
<body>

<div class="container">
    <h1>ابزار بررسی و عیب‌یابی اتصال دیتابیس (Iran Service)</h1>

    <!-- 1. بررسی وجود فایل .env -->
    <div class="section">
        <div class="section-title">مرحله ۱: بررسی فایل تنظیمات (.env)</div>
        <?php
        $envFile = __DIR__ . '/.env';
        if (file_exists($envFile)) {
            echo "<p style='color: #059669; font-weight: bold;'>✓ فایل <span class='code'>.env</span> در مسیر اصلی پیدا شد.</p>";
            
            // پارس مجدد برای نمایش متغیرها
            $lines = file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
            $parsed = array();
            foreach ($lines as $line) {
                $line = trim($line);
                if (empty($line) || strpos($line, '#') === 0) continue;
                if (strpos($line, '=') !== false) {
                    list($name, $value) = explode('=', $line, 2);
                    $name = trim($name);
                    $value = trim($value);
                    
                    // هندل کردن نقل قول ها
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
                    $parsed[$name] = $value;
                }
            }
            
            // نمایش مقادیر لود شده (رمز عبور برای امنیت ماسک می‌شود)
            $dbHost = isset($parsed['DB_HOST']) ? $parsed['DB_HOST'] : 'تنظیم نشده (پیش‌فرض: localhost)';
            $dbUser = isset($parsed['DB_USER']) ? $parsed['DB_USER'] : 'تنظیم نشده (پیش‌فرض: cubxrhuv_siteuser)';
            $dbName = isset($parsed['DB_NAME']) ? $parsed['DB_NAME'] : 'تنظیم نشده (پیش‌فرض: cubxrhuv_site.bniaz)';
            $dbPassRaw = isset($parsed['DB_PASS']) ? $parsed['DB_PASS'] : '';
            
            // ماسک کردن پسورد
            if (!empty($dbPassRaw)) {
                $len = strlen($dbPassRaw);
                $dbPassMasked = ($len > 3) ? substr($dbPassRaw, 0, 2) . str_repeat('*', $len - 3) . substr($dbPassRaw, -1) : '***';
                $dbPassStatus = "ست شده است (طول: $len کاراکتر - شروع با: '" . substr($dbPassRaw, 0, 1) . "' و اتمام با: '" . substr($dbPassRaw, -1) . "')";
            } else {
                $dbPassStatus = "تنظیم نشده (از پیش‌فرض Abbasi163@# استفاده می‌شود)";
                $dbPassRaw = "Abbasi163@#";
            }
            
            echo "<table>";
            echo "<tr><th>میزبان (DB_HOST)</th><td><span class='code'>$dbHost</span></td></tr>";
            echo "<tr><th>نام کاربری دیتابیس (DB_USER)</th><td><span class='code'>$dbUser</span></td></tr>";
            echo "<tr><th>نام دیتابیس (DB_NAME)</th><td><span class='code'>$dbName</span></td></tr>";
            echo "<tr><th>جزئیات رمز عبور (DB_PASS)</th><td>$dbPassStatus</td></tr>";
            echo "</table>";
            
        } else {
            echo "<p style='color: #d97706; font-weight: bold;'>⚠ فایل <span class='code'>.env</span> یافت نشد!</p>";
            echo "<p>کدهای PHP به صورت خودکار از اطلاعات پیش‌فرض گنجانده شده در خود فایل استفاده خواهند کرد:</p>";
            $dbHost = 'localhost';
            $dbUser = 'cubxrhuv_siteuser';
            $dbName = 'cubxrhuv_site.bniaz';
            $dbPassRaw = 'Abbasi163@#';
            
            echo "<table>";
            echo "<tr><th>میزبان پیش‌فرض</th><td><span class='code'>$dbHost</span></td></tr>";
            echo "<tr><th>کاربر پیش‌فرض</th><td><span class='code'>$dbUser</span></td></tr>";
            echo "<tr><th>دیتابیس پیش‌فرض</th><td><span class='code'>$dbName</span></td></tr>";
            echo "</table>";
        }
        ?>
    </div>

    <!-- 2. تلاش برای اتصال به دیتابیس -->
    <?php
    $finalHost = isset($parsed['DB_HOST']) ? $parsed['DB_HOST'] : 'localhost';
    $finalUser = isset($parsed['DB_USER']) ? $parsed['DB_USER'] : 'cubxrhuv_siteuser';
    $finalPass = isset($parsed['DB_PASS']) ? $parsed['DB_PASS'] : 'Abbasi163@#';
    $finalDb   = isset($parsed['DB_NAME']) ? $parsed['DB_NAME'] : 'cubxrhuv_site.bniaz';

    // آزمون گزینه‌های نام مختلف (مانند کد اصلی)
    $dbOpts = array($finalDb, 'cubxrhuv_site.bniaz', 'cubxrhuv_site_bniaz', 'cubxrhuv_site');
    $connected = false;
    $lastError = '';
    $successfulDbName = '';

    foreach ($dbOpts as $dbOpt) {
        try {
            $testPdo = new PDO(
                "mysql:host=$finalHost;dbname=$dbOpt;charset=utf8mb4",
                $finalUser,
                $finalPass,
                array(
                    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                    PDO::TIMEOUT => 5
                )
            );
            if ($testPdo) {
                $connected = true;
                $successfulDbName = $dbOpt;
                break;
            }
        } catch (PDOException $e) {
            $lastError = $e->getMessage();
        }
    }
    ?>

    <?php if ($connected): ?>
        <div class="section status-success">
            <div class="section-title">✓ تبریک! اتصال با موفقیت برقرار شد</div>
            <p>سایت با موفقیت توانست به دیتابیس MySQL در سی‌پنل متصل شود.</p>
            <p>نام دیتابیس متصل شده نهایی: <span class="code"><?php echo htmlspecialchars($successfulDbName); ?></span></p>
            <p>الآن تمام بخش‌های ثبت‌نام، ورود و پنل نمایندگی بدون مشکل کار خواهند کرد.</p>
        </div>
    <?php else: ?>
        <div class="section status-danger">
            <div class="section-title">✗ خطا! اتصال به دیتابیس برقرار نشد</div>
            <p><strong>متن خطای سرور اصلی MySQL:</strong></p>
            <p class="code" style="display: block; padding: 15px; background-color: #fef2f2; border: 1px solid #fca5a5; color: #991b1b; text-align: left; overflow-x: auto;">
                <?php echo htmlspecialchars($lastError); ?>
            </p>

            <div class="guide-box">
                <strong>چگونه این خطا را کاملاً برطرف کنیم؟ (آموزش تصویری/گام به گام در سی‌پنل)</strong>
                <p>خطای فوق (Access denied) بدین معناست که سیستم با نام کاربری و رمز وارد شده قصد اتصال دارد، اما سرور دیتابیس سی‌پنل آن را رد می‌کند. لطفاً گام‌های زیر را به دقت تایید کنید:</p>
                <ol>
                    <li>
                        <strong>گام ویژه و حیاتی (اتصال کاربر به دیتابیس):</strong>
                        در سی‌پنل خود به بخش <strong>MySQL Databases</strong> بروید. به پایین صفحه اسکرول کنید تا به بخش <strong>Add User To Database</strong> برسید.
                        <br>در فیلد User کاربر <span class="code"><?php echo htmlspecialchars($finalUser); ?></span> و در فیلد Database دیتابیس <span class="code"><?php echo htmlspecialchars($finalDb); ?></span> را انتخاب کنید و روی دکمه <strong>Add</strong> کلیک کنید.
                        <br>سپس در صفحه بعد، تیک <strong>ALL PRIVILEGES</strong> را بزنید و دکمه <strong>Make Changes</strong> را بفشارید. 
                        <br><span style="color: #b91c1c; font-weight: bold;">(بدون این مرحله، حتی با رمز درست نیز با خطای Access denied مواجه می‌شوید!)</span>
                    </li>
                    <li>
                        <strong>بررسی مجدد رمز عبور (DB_PASS):</strong>
                        آیا رمز عبوری که در سی‌پنل برای کاربر <span class="code"><?php echo htmlspecialchars($finalUser); ?></span> تعریف کردید ضبط شده در ذهن دارید؟
                        <br>مطمئن شوید که رمز به درستی در فایل <span class="code">.env</span> مقابل <span class="code">DB_PASS</span> نوشته شده و بدون فاصله اضافی داخل نقل قول قرار دارد:
                        <br><span class="code">DB_PASS="رمز_دقیق_سی_پنل"</span>
                    </li>
                    <li>
                        <strong>تغییر مجدد رمز عبور برای تست قطعی:</strong>
                        اگر در صحت رمز شک دارید، در بخش <strong>MySQL Databases</strong> به انتهای صفحه بروید و روی کاربر <span class="code"><?php echo htmlspecialchars($finalUser); ?></span> گزینه Change Password را بزنید. رمز جدیدی مثل <span class="code">Abbasi163@#</span> تعیین کنید و همان را در فایل <span class="code">.env</span> ذخیره و تست نمایید.
                    </li>
                </ol>
            </div>
        </div>
    <?php endif; ?>

    <div style="font-size: 11px; text-align: center; color: #9ca3af; margin-top: 30px; border-top: 1px dashed #e5e7eb; padding-top: 15px;">
        برنامه مدیریت خدمات فنی و عیب‌یابی ایران‌سرویس - ابزار تست دیتابیس سی‌پنل
    </div>
</div>

</body>
</html>
