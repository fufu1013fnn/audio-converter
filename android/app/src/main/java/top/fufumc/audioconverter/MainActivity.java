package top.fufumc.audioconverter;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_AUDIO = 1001;
    private static final int COLOR_PAGE = 0xFFF4F7FA;
    private static final int COLOR_TEXT = 0xFF192231;
    private static final int COLOR_MUTED = 0xFF687489;
    private static final int COLOR_ACCENT = 0xFF146C5C;

    private final List<AudioItem> audioItems = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LinearLayout listContainer;
    private TextView statusText;
    private TextView countText;
    private ProgressBar progressBar;
    private Spinner formatSpinner;
    private Button convertButton;
    private Button clearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        refreshList();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(COLOR_PAGE);

        TextView title = new TextView(this);
        title.setText("音频转换器");
        title.setTextSize(30);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("安卓版第一版支持把系统可解码音频转换为 WAV。");
        subtitle.setTextSize(14);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setPadding(0, dp(6), 0, dp(16));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout settingsCard = createCard();
        settingsCard.setOrientation(LinearLayout.VERTICAL);
        settingsCard.addView(createLabel("输出格式"));
        formatSpinner = createSpinner(new String[]{"wav"}, 0);
        settingsCard.addView(formatSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(settingsCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(14), 0, dp(14));

        Button pickButton = createButton("选择音频", false);
        pickButton.setOnClickListener(v -> openPicker());
        actions.addView(pickButton, new LinearLayout.LayoutParams(0, dp(48), 1));

        clearButton = createButton("清空", false);
        clearButton.setOnClickListener(v -> {
            audioItems.clear();
            refreshList();
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        clearParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(clearButton, clearParams);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout listCard = createCard();
        listCard.setOrientation(LinearLayout.VERTICAL);

        LinearLayout listHead = new LinearLayout(this);
        listHead.setOrientation(LinearLayout.HORIZONTAL);
        listHead.setGravity(Gravity.CENTER_VERTICAL);

        TextView queueTitle = new TextView(this);
        queueTitle.setText("文件队列");
        queueTitle.setTextSize(18);
        queueTitle.setTypeface(null, 1);
        queueTitle.setTextColor(COLOR_TEXT);
        listHead.addView(queueTitle, new LinearLayout.LayoutParams(0, -2, 1));

        countText = new TextView(this);
        countText.setTextColor(COLOR_MUTED);
        countText.setGravity(Gravity.END);
        listHead.addView(countText, new LinearLayout.LayoutParams(-2, -2));
        listCard.addView(listHead, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scrollView = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(10), 0, 0);
        scrollView.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        listCard.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(listCard, new LinearLayout.LayoutParams(-1, 0, 1));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(12));
        progressParams.setMargins(0, dp(14), 0, dp(8));
        root.addView(progressBar, progressParams);

        convertButton = createButton("开始转换", true);
        convertButton.setOnClickListener(v -> convertAll());
        root.addView(convertButton, new LinearLayout.LayoutParams(-1, dp(52)));

        statusText = new TextView(this);
        statusText.setTextColor(COLOR_MUTED);
        statusText.setTextSize(13);
        statusText.setPadding(0, dp(10), 0, 0);
        root.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        return root;
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundColor(0xFFFFFFFF);
        return card;
    }

    private TextView createLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTypeface(null, 1);
        label.setTextColor(COLOR_MUTED);
        label.setPadding(0, 0, 0, dp(6));
        return label;
    }

    private Spinner createSpinner(String[] values, int selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        return spinner;
    }

    private Button createButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(null, 1);
        button.setTextColor(primary ? 0xFFFFFFFF : COLOR_TEXT);
        button.setBackgroundColor(primary ? COLOR_ACCENT : 0xFFFFFFFF);
        return button;
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_AUDIO || resultCode != RESULT_OK || data == null) return;
        if (data.getClipData() != null) {
            for (int index = 0; index < data.getClipData().getItemCount(); index++) {
                addUri(data.getClipData().getItemAt(index).getUri());
            }
        } else if (data.getData() != null) {
            addUri(data.getData());
        }
        refreshList();
    }

    private void addUri(Uri uri) {
        for (AudioItem item : audioItems) {
            if (item.uri.equals(uri)) return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        audioItems.add(new AudioItem(uri, getDisplayName(uri)));
    }

    private void refreshList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        countText.setText(audioItems.size() + " 个文件");
        clearButton.setEnabled(!audioItems.isEmpty());
        convertButton.setEnabled(!audioItems.isEmpty());

        if (audioItems.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有选择音频文件。");
            empty.setTextColor(COLOR_MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(28));
            listContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            statusText.setText("请选择音频文件。");
            return;
        }

        for (AudioItem item : audioItems) {
            TextView row = new TextView(this);
            row.setText(item.name + "\n" + item.status);
            row.setTextSize(14);
            row.setTextColor(COLOR_TEXT);
            row.setPadding(0, dp(10), 0, dp(10));
            listContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        statusText.setText("已准备好转换 " + audioItems.size() + " 个文件。");
    }

    private void convertAll() {
        if (audioItems.isEmpty()) return;
        setWorking(true);
        progressBar.setProgress(0);
        executor.execute(() -> {
            int success = 0;
            for (int index = 0; index < audioItems.size(); index++) {
                AudioItem item = audioItems.get(index);
                try {
                    updateItemStatus(item, "正在读取...");
                    File input = copyToCache(item);
                    File output = createOutputFile(item.name);
                    updateItemStatus(item, "正在转换为 WAV...");
                    decodeToWav(input, output);
                    updateItemStatus(item, "完成：" + output.getName());
                    success++;
                } catch (Exception error) {
                    updateItemStatus(item, "失败：" + error.getMessage());
                }
                int finalProgress = Math.round(((index + 1) * 100f) / audioItems.size());
                runOnUiThread(() -> progressBar.setProgress(finalProgress));
            }
            int finalSuccess = success;
            runOnUiThread(() -> {
                setWorking(false);
                statusText.setText("完成：成功转换 " + finalSuccess + " / " + audioItems.size() + " 个文件。");
                Toast.makeText(this, "转换完成", Toast.LENGTH_LONG).show();
            });
        });
    }

    private File copyToCache(AudioItem item) throws IOException {
        ContentResolver resolver = getContentResolver();
        File file = new File(getCacheDir(), sanitizeFileName(item.name));
        try (InputStream input = resolver.openInputStream(item.uri);
             FileOutputStream output = new FileOutputStream(file)) {
            if (input == null) throw new IOException("无法读取文件");
            byte[] buffer = new byte[1024 * 64];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        return file;
    }

    private File createOutputFile(String sourceName) throws IOException {
        File directory = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
        if (directory == null) directory = getFilesDir();
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("无法创建输出目录");
        String base = stripExtension(sanitizeFileName(sourceName));
        File output = new File(directory, base + ".wav");
        int counter = 2;
        while (output.exists()) {
            output = new File(directory, String.format(Locale.US, "%s (%d).wav", base, counter));
            counter++;
        }
        return output;
    }

    private void decodeToWav(File input, File output) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(input.getAbsolutePath());
            int audioTrack = selectAudioTrack(extractor);
            if (audioTrack < 0) throw new IOException("找不到音频轨道");
            extractor.selectTrack(audioTrack);
            MediaFormat format = extractor.getTrackFormat(audioTrack);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) throw new IOException("未知音频格式");
            int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = decoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        if (inputBuffer == null) throw new IOException("解码输入缓冲区不可用");
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = decoder.dequeueOutputBuffer(info, 10000);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && info.size > 0) {
                        byte[] chunk = new byte[info.size];
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        outputBuffer.get(chunk);
                        pcm.write(chunk);
                    }
                    decoder.releaseOutputBuffer(outputIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            writeWav(output, pcm.toByteArray(), sampleRate, channels);
        } finally {
            extractor.release();
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
        }
    }

    private int selectAudioTrack(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); index++) {
            MediaFormat format = extractor.getTrackFormat(index);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return index;
        }
        return -1;
    }

    private void writeWav(File output, byte[] pcm, int sampleRate, int channels) throws IOException {
        int byteRate = sampleRate * channels * 2;
        int totalDataLen = pcm.length + 36;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'});
        header.putInt(totalDataLen);
        header.put(new byte[]{'W', 'A', 'V', 'E'});
        header.put(new byte[]{'f', 'm', 't', ' '});
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) (channels * 2));
        header.putShort((short) 16);
        header.put(new byte[]{'d', 'a', 't', 'a'});
        header.putInt(pcm.length);

        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(header.array());
            stream.write(pcm);
        }
    }

    private void updateItemStatus(AudioItem item, String status) {
        item.status = status;
        runOnUiThread(this::refreshList);
    }

    private void setWorking(boolean working) {
        convertButton.setEnabled(!working && !audioItems.isEmpty());
        clearButton.setEnabled(!working && !audioItems.isEmpty());
        formatSpinner.setEnabled(!working);
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? "audio" : last;
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String stripExtension(String value) {
        int index = value.lastIndexOf('.');
        return index > 0 ? value.substring(0, index) : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AudioItem {
        final Uri uri;
        final String name;
        String status = "等待转换";

        AudioItem(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }
}
