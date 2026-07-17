package top.fufumc.audioconverter;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
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
    private static final int COLOR_PANEL = 0xFFFFFFFF;
    private static final int COLOR_PANEL_SOFT = 0xFFF9FBFD;
    private static final int COLOR_TEXT = 0xFF172033;
    private static final int COLOR_MUTED = 0xFF667085;
    private static final int COLOR_ACCENT = 0xFF146C5C;
    private static final int COLOR_WARM = 0xFFB7532C;
    private static final int COLOR_LINE = 0xFFD8DEE9;

    private final List<AudioItem> audioItems = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LinearLayout listContainer;
    private TextView statusText;
    private TextView countText;
    private ProgressBar progressBar;
    private Spinner formatSpinner;
    private Spinner qualitySpinner;
    private Button convertButton;
    private Button clearButton;
    private Button pickButton;

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
        root.setPadding(dp(18), dp(18), dp(18), dp(16));
        root.setBackgroundColor(COLOR_PAGE);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(0, dp(4), 0, dp(18));
        root.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("音频转换器");
        title.setTextSize(31);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hero.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("轻量本地转换，支持 WAV 与 M4A(AAC) 输出。");
        subtitle.setTextSize(14);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setPadding(0, dp(6), 0, 0);
        hero.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout settingsCard = createCard(COLOR_PANEL);
        settingsCard.setOrientation(LinearLayout.VERTICAL);
        root.addView(settingsCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout settingRow = new LinearLayout(this);
        settingRow.setOrientation(LinearLayout.HORIZONTAL);
        settingsCard.addView(settingRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout formatGroup = createFieldGroup("输出格式");
        formatSpinner = createSpinner(new String[]{"wav", "m4a"}, 0);
        formatGroup.addView(formatSpinner, new LinearLayout.LayoutParams(-1, dp(46)));
        settingRow.addView(formatGroup, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout qualityGroup = createFieldGroup("M4A 质量");
        qualitySpinner = createSpinner(new String[]{"标准 192k", "高质量 256k", "较小 128k"}, 0);
        LinearLayout.LayoutParams qualityParams = new LinearLayout.LayoutParams(0, -2, 1);
        qualityParams.setMargins(dp(12), 0, 0, 0);
        qualityGroup.addView(qualitySpinner, new LinearLayout.LayoutParams(-1, dp(46)));
        settingRow.addView(qualityGroup, qualityParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(14), 0, dp(14));
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        pickButton = createButton("选择音频", false);
        pickButton.setOnClickListener(v -> openPicker());
        actions.addView(pickButton, new LinearLayout.LayoutParams(0, dp(50), 1));

        clearButton = createButton("清空", false);
        clearButton.setOnClickListener(v -> {
            audioItems.clear();
            refreshList();
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        clearParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(clearButton, clearParams);

        LinearLayout listCard = createCard(COLOR_PANEL);
        listCard.setOrientation(LinearLayout.VERTICAL);
        root.addView(listCard, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout listHead = new LinearLayout(this);
        listHead.setGravity(Gravity.CENTER_VERTICAL);
        listHead.setOrientation(LinearLayout.HORIZONTAL);
        listCard.addView(listHead, new LinearLayout.LayoutParams(-1, -2));

        TextView queueTitle = new TextView(this);
        queueTitle.setText("文件队列");
        queueTitle.setTextSize(18);
        queueTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        queueTitle.setTextColor(COLOR_TEXT);
        listHead.addView(queueTitle, new LinearLayout.LayoutParams(0, -2, 1));

        countText = new TextView(this);
        countText.setTextColor(COLOR_MUTED);
        countText.setTextSize(13);
        countText.setGravity(Gravity.END);
        listHead.addView(countText, new LinearLayout.LayoutParams(-2, -2));

        ScrollView scrollView = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(12), 0, 0);
        scrollView.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        listCard.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(10));
        progressParams.setMargins(0, dp(14), 0, dp(9));
        root.addView(progressBar, progressParams);

        convertButton = createButton("开始转换", true);
        convertButton.setOnClickListener(v -> convertAll());
        root.addView(convertButton, new LinearLayout.LayoutParams(-1, dp(54)));

        statusText = new TextView(this);
        statusText.setTextColor(COLOR_MUTED);
        statusText.setTextSize(13);
        statusText.setPadding(0, dp(10), 0, 0);
        root.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        return root;
    }

    private LinearLayout createCard(int color) {
        LinearLayout card = new LinearLayout(this);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), COLOR_LINE);
        card.setBackground(drawable);
        return card;
    }

    private LinearLayout createFieldGroup(String labelText) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextSize(12);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(COLOR_MUTED);
        label.setPadding(0, 0, 0, dp(6));
        group.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    private Spinner createSpinner(String[] values, int selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setBackground(makeRounded(COLOR_PANEL_SOFT, COLOR_LINE, 12));
        return spinner;
    }

    private Button createButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : COLOR_TEXT);
        button.setBackground(makeRounded(primary ? COLOR_ACCENT : COLOR_PANEL, primary ? COLOR_ACCENT : COLOR_LINE, 14));
        return button;
    }

    private GradientDrawable makeRounded(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
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
            LinearLayout emptyCard = createCard(COLOR_PANEL_SOFT);
            emptyCard.setGravity(Gravity.CENTER);
            TextView empty = new TextView(this);
            empty.setText("还没有选择音频文件");
            empty.setTextColor(COLOR_MUTED);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            emptyCard.addView(empty, new LinearLayout.LayoutParams(-1, dp(72)));
            listContainer.addView(emptyCard, new LinearLayout.LayoutParams(-1, -2));
            statusText.setText("请选择音频文件。");
            return;
        }

        for (AudioItem item : audioItems) {
            LinearLayout row = createCard(COLOR_PANEL_SOFT);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, 0, 0, dp(8));

            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextSize(18);
            dot.setTextColor(COLOR_WARM);
            row.addView(dot, new LinearLayout.LayoutParams(dp(28), -2));

            TextView text = new TextView(this);
            text.setText(item.name + "\n" + item.status);
            text.setTextSize(14);
            text.setTextColor(COLOR_TEXT);
            text.setLineSpacing(dp(2), 1.0f);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            listContainer.addView(row, rowParams);
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
                File raw = null;
                try {
                    updateItemStatus(item, "正在读取...");
                    File input = copyToCache(item);
                    String outputFormat = (String) formatSpinner.getSelectedItem();
                    File output = createOutputFile(item.name, outputFormat);
                    raw = File.createTempFile("audio-converter-", ".pcm", getCacheDir());
                    updateItemStatus(item, "正在解码...");
                    AudioSpec spec = decodeToRawPcm(input, raw);
                    updateItemStatus(item, "正在编码 " + outputFormat.toUpperCase(Locale.US) + "...");
                    if ("m4a".equals(outputFormat)) {
                        encodeRawPcmToM4a(raw, output, spec);
                    } else {
                        writeWavFromRaw(raw, output, spec);
                    }
                    updateItemStatus(item, "完成：" + output.getName());
                    success++;
                } catch (Exception error) {
                    updateItemStatus(item, "失败：" + error.getMessage());
                } finally {
                    if (raw != null) raw.delete();
                }
                int finalProgress = Math.round(((index + 1) * 100f) / audioItems.size());
                runOnUiThread(() -> progressBar.setProgress(finalProgress));
            }
            int finalSuccess = success;
            runOnUiThread(() -> {
                setWorking(false);
                statusText.setText("完成：成功转换 " + finalSuccess + " / " + audioItems.size() + " 个文件。输出在应用专属音乐目录。");
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

    private File createOutputFile(String sourceName, String extension) throws IOException {
        File directory = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
        if (directory == null) directory = getFilesDir();
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("无法创建输出目录");
        String base = stripExtension(sanitizeFileName(sourceName));
        File output = new File(directory, base + "." + extension);
        int counter = 2;
        while (output.exists()) {
            output = new File(directory, String.format(Locale.US, "%s (%d).%s", base, counter, extension));
            counter++;
        }
        return output;
    }

    private AudioSpec decodeToRawPcm(File input, File rawOutput) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try (FileOutputStream rawStream = new FileOutputStream(rawOutput)) {
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

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            long pcmBytes = 0;

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
                        rawStream.write(chunk);
                        pcmBytes += chunk.length;
                    }
                    decoder.releaseOutputBuffer(outputIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            return new AudioSpec(sampleRate, channels, pcmBytes);
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

    private void writeWavFromRaw(File raw, File output, AudioSpec spec) throws IOException {
        try (RandomAccessFile wav = new RandomAccessFile(output, "rw");
             FileInputStream input = new FileInputStream(raw)) {
            wav.setLength(0);
            writeWavHeader(wav, spec);
            byte[] buffer = new byte[1024 * 64];
            int read;
            while ((read = input.read(buffer)) != -1) wav.write(buffer, 0, read);
        }
    }

    private void writeWavHeader(RandomAccessFile wav, AudioSpec spec) throws IOException {
        int byteRate = spec.sampleRate * spec.channels * 2;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'});
        header.putInt((int) spec.pcmBytes + 36);
        header.put(new byte[]{'W', 'A', 'V', 'E'});
        header.put(new byte[]{'f', 'm', 't', ' '});
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) spec.channels);
        header.putInt(spec.sampleRate);
        header.putInt(byteRate);
        header.putShort((short) (spec.channels * 2));
        header.putShort((short) 16);
        header.put(new byte[]{'d', 'a', 't', 'a'});
        header.putInt((int) spec.pcmBytes);
        wav.write(header.array());
    }

    private void encodeRawPcmToM4a(File raw, File output, AudioSpec spec) throws IOException {
        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        try (FileInputStream input = new FileInputStream(raw)) {
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, spec.sampleRate, spec.channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, selectedBitrate());
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            boolean muxerStarted = false;
            int trackIndex = -1;
            long presentationTimeUs = 0;
            int bytesPerFrame = Math.max(1, spec.channels * 2);
            byte[] temp = new byte[1024 * 16];

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = encoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = encoder.getInputBuffer(inputIndex);
                        if (inputBuffer == null) throw new IOException("编码输入缓冲区不可用");
                        inputBuffer.clear();
                        int max = Math.min(inputBuffer.remaining(), temp.length);
                        int read = input.read(temp, 0, max);
                        if (read < 0) {
                            encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            inputBuffer.put(temp, 0, read);
                            long frames = read / bytesPerFrame;
                            encoder.queueInputBuffer(inputIndex, 0, read, presentationTimeUs, 0);
                            presentationTimeUs += (frames * 1000000L) / spec.sampleRate;
                        }
                    }
                }

                int outputIndex = encoder.dequeueOutputBuffer(info, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                } else if (outputIndex >= 0) {
                    ByteBuffer encoded = encoder.getOutputBuffer(outputIndex);
                    if (encoded != null && info.size > 0 && muxerStarted) {
                        encoded.position(info.offset);
                        encoded.limit(info.offset + info.size);
                        muxer.writeSampleData(trackIndex, encoded, info);
                    }
                    encoder.releaseOutputBuffer(outputIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (muxer != null) muxer.release();
        }
    }

    private int selectedBitrate() {
        String quality = (String) qualitySpinner.getSelectedItem();
        if (quality != null && quality.contains("256")) return 256000;
        if (quality != null && quality.contains("128")) return 128000;
        return 192000;
    }

    private void updateItemStatus(AudioItem item, String status) {
        item.status = status;
        runOnUiThread(this::refreshList);
    }

    private void setWorking(boolean working) {
        convertButton.setEnabled(!working && !audioItems.isEmpty());
        clearButton.setEnabled(!working && !audioItems.isEmpty());
        pickButton.setEnabled(!working);
        formatSpinner.setEnabled(!working);
        qualitySpinner.setEnabled(!working);
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

    private static final class AudioSpec {
        final int sampleRate;
        final int channels;
        final long pcmBytes;

        AudioSpec(int sampleRate, int channels, long pcmBytes) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.pcmBytes = pcmBytes;
        }
    }
}
