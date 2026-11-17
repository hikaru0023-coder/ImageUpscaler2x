package com.example.imageupscaler2x;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.util.TypedValue;

import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_FOLDER = 1001;
    private static final long MAX_OUTPUT_PIXELS = 24_000_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView folderText;
    private TextView summaryText;
    private TextView logText;
    private ProgressBar progressBar;
    private Button chooseButton;
    private Button startButton;
    private Button cancelButton;
    private Uri folderUri;
    private volatile boolean cancelRequested;
    private boolean processing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(24));
        page.setBackgroundColor(Color.rgb(245, 247, 251));
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("圖片批次放大 2 倍");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(23, 32, 51));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("批次放大資料夾中的 JPG、PNG、WebP 圖片");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(91, 100, 117));
        LinearLayout.LayoutParams subParams = wrap();
        subParams.topMargin = dp(8);
        page.addView(subtitle, subParams);

        TextView example = new TextView(this);
        example.setText("尺寸示例：1500 × 3000  →  3000 × 6000");
        example.setTextSize(14);
        example.setTextColor(Color.rgb(71, 84, 103));
        example.setPadding(dp(12), dp(12), dp(12), dp(12));
        example.setBackgroundColor(Color.rgb(255, 240, 220));
        LinearLayout.LayoutParams exampleParams = wrap();
        exampleParams.topMargin = dp(18);
        page.addView(example, exampleParams);

        chooseButton = button("選擇圖片資料夾");
        LinearLayout.LayoutParams chooseParams = wrap();
        chooseParams.topMargin = dp(18);
        page.addView(chooseButton, chooseParams);
        chooseButton.setOnClickListener(v -> chooseFolder());

        folderText = new TextView(this);
        folderText.setText("尚未選擇資料夾");
        folderText.setTextSize(14);
        folderText.setTextColor(Color.rgb(52, 64, 84));
        folderText.setPadding(dp(12), dp(12), dp(12), dp(12));
        folderText.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams folderParams = wrap();
        folderParams.topMargin = dp(10);
        page.addView(folderText, folderParams);

        startButton = button("開始批次放大");
        LinearLayout.LayoutParams startParams = wrap();
        startParams.topMargin = dp(18);
        page.addView(startButton, startParams);
        startButton.setOnClickListener(v -> startProcessing());

        cancelButton = button("取消處理");
        cancelButton.setEnabled(false);
        LinearLayout.LayoutParams cancelParams = wrap();
        cancelParams.topMargin = dp(8);
        page.addView(cancelButton, cancelParams);
        cancelButton.setOnClickListener(v -> {
            cancelRequested = true;
            cancelButton.setEnabled(false);
            summaryText.setText("正在取消，會完成目前這張圖片後停止…");
        });

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams progressParams = wrap();
        progressParams.topMargin = dp(18);
        page.addView(progressBar, progressParams);

        summaryText = new TextView(this);
        summaryText.setText("等待選擇資料夾");
        summaryText.setTextSize(14);
        summaryText.setTextColor(Color.rgb(52, 64, 84));
        summaryText.setPadding(0, dp(12), 0, dp(8));
        page.addView(summaryText);

        TextView logTitle = new TextView(this);
        logTitle.setText("處理紀錄");
        logTitle.setTextSize(16);
        logTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        logTitle.setTextColor(Color.rgb(23, 32, 51));
        logTitle.setPadding(0, dp(14), 0, dp(8));
        page.addView(logTitle);

        logText = new TextView(this);
        logText.setText("支援 JPG、JPEG、PNG、WebP。\n結果會存入來源資料夾內的「圖片放大2倍」資料夾。\n原始圖片不會被覆蓋。");
        logText.setTextSize(13);
        logText.setTextColor(Color.rgb(91, 100, 117));
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logText.setBackgroundColor(Color.WHITE);
        page.addView(logText, wrap());

        setContentView(scroll);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        return b;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void chooseFolder() {
        if (processing) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (SecurityException ignored) {
                // Some document providers do not grant persistable access.
            }
            folderUri = uri;
            DocumentFile folder = DocumentFile.fromTreeUri(this, uri);
            folderText.setText(folder != null ? "已選擇：" + folder.getName() : "已選擇資料夾");
            summaryText.setText("資料夾已就緒，可以開始處理");
        }
    }

    private void startProcessing() {
        if (processing) return;
        if (folderUri == null) {
            summaryText.setText("請先選擇圖片資料夾");
            return;
        }
        DocumentFile sourceFolder = DocumentFile.fromTreeUri(this, folderUri);
        if (sourceFolder == null || !sourceFolder.isDirectory()) {
            summaryText.setText("無法讀取所選資料夾，請重新選擇");
            return;
        }

        DocumentFile[] children = sourceFolder.listFiles();
        List<DocumentFile> images = new ArrayList<>();
        if (children != null) {
            for (DocumentFile file : children) {
                if (file.isFile() && isSupported(file.getName())) images.add(file);
            }
        }
        if (images.isEmpty()) {
            summaryText.setText("資料夾第一層沒有可處理的圖片");
            return;
        }

        processing = true;
        cancelRequested = false;
        startButton.setEnabled(false);
        chooseButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progressBar.setMax(images.size());
        progressBar.setProgress(0);
        logText.setText("");
        summaryText.setText("找到 " + images.size() + " 張圖片，開始處理…");

        executor.execute(() -> processImages(sourceFolder, images));
    }

    private void processImages(DocumentFile sourceFolder, List<DocumentFile> images) {
        DocumentFile outputFolder = sourceFolder.findFile("圖片放大2倍");
        if (outputFolder == null) {
            outputFolder = sourceFolder.createDirectory("圖片放大2倍");
        }
        if (outputFolder == null) {
            finishProcessing("無法建立輸出資料夾", 0, 0, 1);
            return;
        }

        int success = 0;
        int skipped = 0;
        int failed = 0;
        int done = 0;

        for (DocumentFile image : images) {
            if (cancelRequested) break;
            try {
                ProcessResult result = enlargeOne(image, outputFolder);
                if (result.success) {
                    success++;
                    appendLog("完成：" + result.name);
                } else {
                    skipped++;
                    appendLog("略過：" + image.getName() + "（" + result.message + "）");
                }
            } catch (OutOfMemoryError error) {
                failed++;
                appendLog("失敗：" + image.getName() + "（記憶體不足）");
                System.gc();
            } catch (Exception error) {
                failed++;
                appendLog("失敗：" + image.getName() + "（" + safeMessage(error) + "）");
            }
            done++;
            updateProgress(done, images.size(),
                    "處理中 " + done + "/" + images.size() + "｜成功 " + success
                            + "｜略過 " + skipped + "｜失敗 " + failed);
        }

        String ending = cancelRequested ? "已取消後續處理" : "批次處理完成";
        finishProcessing(ending, success, skipped, failed);
    }

    private ProcessResult enlargeOne(DocumentFile source, DocumentFile outputFolder) throws Exception {
        String name = source.getName();
        if (name == null) return ProcessResult.skip("檔名無效");
        String lower = name.toLowerCase(Locale.ROOT);
        String extension = lower.substring(lower.lastIndexOf('.') + 1);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(source.getUri())) {
            if (in == null) return ProcessResult.skip("無法讀取檔案");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ProcessResult.skip("不是有效圖片");

        long outWidth = (long) bounds.outWidth * 2L;
        long outHeight = (long) bounds.outHeight * 2L;
        if (outWidth * outHeight > MAX_OUTPUT_PIXELS) {
            return ProcessResult.skip("放大後超過 2,400 萬像素上限");
        }

        Bitmap original;
        try (InputStream in = getContentResolver().openInputStream(source.getUri())) {
            if (in == null) return ProcessResult.skip("無法讀取圖片資料");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            original = BitmapFactory.decodeStream(in, null, options);
        }
        if (original == null) return ProcessResult.skip("圖片解碼失敗");

        Bitmap enlarged = null;
        try {
            enlarged = Bitmap.createScaledBitmap(original, (int) outWidth, (int) outHeight, true);
        } finally {
            if (original != enlarged) original.recycle();
        }
        if (enlarged == null) return ProcessResult.skip("圖片放大失敗");

        String outputName = uniqueName(outputFolder, name);
        String mime = extension.equals("png") ? "image/png"
                : extension.equals("webp") ? "image/webp" : "image/jpeg";
        DocumentFile output = outputFolder.createFile(mime, outputName);
        if (output == null) {
            enlarged.recycle();
            return ProcessResult.skip("無法建立輸出檔案");
        }

        boolean compressed;
        try (OutputStream out = getContentResolver().openOutputStream(output.getUri(), "w")) {
            if (out == null) throw new IllegalStateException("無法寫入輸出檔案");
            Bitmap.CompressFormat format;
            int quality;
            if (extension.equals("png")) {
                format = Bitmap.CompressFormat.PNG;
                quality = 100;
            } else if (extension.equals("webp")) {
                format = Bitmap.CompressFormat.WEBP;
                quality = 95;
            } else {
                format = Bitmap.CompressFormat.JPEG;
                quality = 95;
            }
            compressed = enlarged.compress(format, quality, out);
            out.flush();
        } finally {
            enlarged.recycle();
        }

        if (!compressed) {
            output.delete();
            return ProcessResult.skip("圖片寫入失敗");
        }
        return ProcessResult.ok(outputName);
    }

    private String uniqueName(DocumentFile folder, String originalName) {
        int dot = originalName.lastIndexOf('.');
        String base = dot > 0 ? originalName.substring(0, dot) : originalName;
        String ext = dot > 0 ? originalName.substring(dot) : "";
        String candidate = base + "_2x" + ext;
        int index = 1;
        while (folder.findFile(candidate) != null) {
            candidate = base + "_2x(" + index + ")" + ext;
            index++;
        }
        return candidate;
    }

    private boolean isSupported(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName() : message;
    }

    private void updateProgress(int done, int total, String message) {
        mainHandler.post(() -> {
            progressBar.setMax(Math.max(1, total));
            progressBar.setProgress(done);
            summaryText.setText(message);
        });
    }

    private void appendLog(String line) {
        mainHandler.post(() -> logText.append("\n" + line));
    }

    private void finishProcessing(String message, int success, int skipped, int failed) {
        mainHandler.post(() -> {
            processing = false;
            startButton.setEnabled(true);
            chooseButton.setEnabled(true);
            cancelButton.setEnabled(false);
            summaryText.setText(message + "｜成功 " + success + "｜略過 " + skipped
                    + "｜失敗 " + failed);
        });
    }

    private static class ProcessResult {
        final boolean success;
        final String name;
        final String message;

        private ProcessResult(boolean success, String name, String message) {
            this.success = success;
            this.name = name;
            this.message = message;
        }

        static ProcessResult ok(String name) {
            return new ProcessResult(true, name, "");
        }

        static ProcessResult skip(String message) {
            return new ProcessResult(false, "", message);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelRequested = true;
        executor.shutdown();
    }
}
