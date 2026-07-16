using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Text;
using System.Windows.Forms;

namespace AudioConverterApp
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }

    public sealed class MainForm : Form
    {
        private ListBox fileList;
        private ComboBox formatBox;
        private ComboBox qualityBox;
        private TextBox outputBox;
        private TextBox ffmpegBox;
        private ProgressBar progressBar;
        private Label statusLabel;
        private Label countLabel;
        private Button convertButton;
        private Button clearButton;
        private readonly List<string> files = new List<string>();
        private string bundledFfmpegPath = string.Empty;

        private static readonly Color PageColor = Color.FromArgb(244, 247, 250);
        private static readonly Color PanelColor = Color.White;
        private static readonly Color TextColor = Color.FromArgb(25, 34, 49);
        private static readonly Color MutedColor = Color.FromArgb(104, 116, 137);
        private static readonly Color LineColor = Color.FromArgb(220, 226, 235);
        private static readonly Color AccentColor = Color.FromArgb(20, 108, 92);
        private static readonly Color AccentHoverColor = Color.FromArgb(16, 91, 78);
        private static readonly Color WarmColor = Color.FromArgb(183, 83, 44);

        public MainForm()
        {
            Text = "音频转换器";
            Width = 980;
            Height = 660;
            MinimumSize = new Size(860, 560);
            StartPosition = FormStartPosition.CenterScreen;
            Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point);
            BackColor = PageColor;
            AllowDrop = true;

            var root = new TableLayoutPanel();
            root.Dock = DockStyle.Fill;
            root.Padding = new Padding(24);
            root.BackColor = PageColor;
            root.RowCount = 3;
            root.ColumnCount = 1;
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 92));
            root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 78));
            Controls.Add(root);

            root.Controls.Add(CreateHeader(), 0, 0);

            var content = new TableLayoutPanel();
            content.Dock = DockStyle.Fill;
            content.ColumnCount = 2;
            content.RowCount = 1;
            content.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 64));
            content.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 36));
            content.BackColor = PageColor;
            root.Controls.Add(content, 0, 1);

            content.Controls.Add(CreateFilePanel(), 0, 0);
            content.Controls.Add(CreateSettingsPanel(), 1, 0);
            root.Controls.Add(CreateFooter(), 0, 2);

            DragEnter += HandleDragEnter;
            DragDrop += HandleDragDrop;
            RefreshFileList();
        }

        private Control CreateHeader()
        {
            var panel = new Panel();
            panel.Dock = DockStyle.Fill;
            panel.BackColor = PageColor;

            var title = new Label();
            title.Text = "音频转换器";
            title.Font = new Font(Font.FontFamily, 25F, FontStyle.Bold);
            title.ForeColor = TextColor;
            title.Location = new Point(2, 4);
            title.Size = new Size(360, 42);
            panel.Controls.Add(title);

            var subtitle = new Label();
            subtitle.Text = "批量转换常见音频格式，拖进来，选格式，然后开始。";
            subtitle.Font = new Font(Font.FontFamily, 10F, FontStyle.Regular);
            subtitle.ForeColor = MutedColor;
            subtitle.Location = new Point(4, 52);
            subtitle.Size = new Size(620, 26);
            panel.Controls.Add(subtitle);

            var badge = new Label();
            badge.Text = "单文件版";
            badge.TextAlign = ContentAlignment.MiddleCenter;
            badge.Font = new Font(Font.FontFamily, 9F, FontStyle.Bold);
            badge.ForeColor = AccentColor;
            badge.BackColor = Color.FromArgb(226, 243, 238);
            badge.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            badge.Size = new Size(120, 32);
            badge.Location = new Point(panel.Width - badge.Width - 4, 16);
            panel.Controls.Add(badge);
            panel.Resize += delegate { badge.Left = panel.Width - badge.Width - 4; };

            return panel;
        }

        private Control CreateFilePanel()
        {
            var card = new CardPanel();
            card.Dock = DockStyle.Fill;
            card.Margin = new Padding(0, 0, 12, 0);
            card.Padding = new Padding(18);
            card.BackColor = PanelColor;
            card.AllowDrop = true;
            card.DragEnter += HandleDragEnter;
            card.DragDrop += HandleDragDrop;

            var layout = new TableLayoutPanel();
            layout.Dock = DockStyle.Fill;
            layout.ColumnCount = 1;
            layout.RowCount = 3;
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 58));
            layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 64));
            layout.BackColor = PanelColor;
            card.Controls.Add(layout);

            var head = new Panel();
            head.Dock = DockStyle.Fill;
            head.BackColor = PanelColor;
            layout.Controls.Add(head, 0, 0);

            var title = new Label();
            title.Text = "文件队列";
            title.Font = new Font(Font.FontFamily, 15F, FontStyle.Bold);
            title.ForeColor = TextColor;
            title.Location = new Point(0, 0);
            title.Size = new Size(150, 30);
            head.Controls.Add(title);

            countLabel = new Label();
            countLabel.TextAlign = ContentAlignment.MiddleRight;
            countLabel.ForeColor = MutedColor;
            countLabel.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            countLabel.Size = new Size(160, 24);
            countLabel.Location = new Point(head.Width - countLabel.Width, 5);
            head.Controls.Add(countLabel);
            head.Resize += delegate { countLabel.Left = head.Width - countLabel.Width; };

            var hint = new Label();
            hint.Text = "支持 mp3、wav、flac、aac、m4a、ogg、opus、wma、webm 等格式。";
            hint.ForeColor = MutedColor;
            hint.Location = new Point(0, 32);
            hint.Size = new Size(640, 24);
            hint.Anchor = AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Top;
            head.Controls.Add(hint);

            fileList = new ListBox();
            fileList.Dock = DockStyle.Fill;
            fileList.BorderStyle = BorderStyle.None;
            fileList.BackColor = Color.FromArgb(249, 251, 253);
            fileList.ForeColor = TextColor;
            fileList.Font = new Font(Font.FontFamily, 10F, FontStyle.Regular);
            fileList.ItemHeight = 34;
            fileList.HorizontalScrollbar = true;
            fileList.AllowDrop = true;
            fileList.DrawMode = DrawMode.OwnerDrawFixed;
            fileList.DrawItem += DrawFileItem;
            fileList.DragEnter += HandleDragEnter;
            fileList.DragDrop += HandleDragDrop;
            layout.Controls.Add(fileList, 0, 1);

            var dropHint = new Label();
            dropHint.Dock = DockStyle.Fill;
            dropHint.Text = "把音频拖到窗口里，或者点击下面的添加文件";
            dropHint.TextAlign = ContentAlignment.MiddleCenter;
            dropHint.ForeColor = MutedColor;
            layout.Controls.Add(dropHint, 0, 2);

            return card;
        }

        private Control CreateSettingsPanel()
        {
            var card = new CardPanel();
            card.Dock = DockStyle.Fill;
            card.Margin = new Padding(12, 0, 0, 0);
            card.Padding = new Padding(18);
            card.BackColor = PanelColor;

            var layout = new TableLayoutPanel();
            layout.Dock = DockStyle.Fill;
            layout.ColumnCount = 1;
            layout.RowCount = 10;
            layout.BackColor = PanelColor;
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 44));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 28));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 42));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 18));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 28));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 42));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 18));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 28));
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 86));
            layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            card.Controls.Add(layout);

            var title = new Label();
            title.Text = "输出设置";
            title.Font = new Font(Font.FontFamily, 15F, FontStyle.Bold);
            title.ForeColor = TextColor;
            title.Dock = DockStyle.Fill;
            layout.Controls.Add(title, 0, 0);

            layout.Controls.Add(CreateFieldLabel("输出格式"), 0, 1);
            formatBox = CreateComboBox(new object[] { "mp3", "wav", "flac", "aac", "ogg", "m4a" }, 0);
            layout.Controls.Add(formatBox, 0, 2);

            layout.Controls.Add(CreateFieldLabel("质量"), 0, 4);
            qualityBox = CreateComboBox(new object[] { "高质量 320k", "标准 192k", "较小 128k", "保持默认" }, 1);
            layout.Controls.Add(qualityBox, 0, 5);

            layout.Controls.Add(CreateFieldLabel("输出目录"), 0, 7);
            var outputPanel = new TableLayoutPanel();
            outputPanel.Dock = DockStyle.Fill;
            outputPanel.ColumnCount = 1;
            outputPanel.RowCount = 2;
            outputPanel.RowStyles.Add(new RowStyle(SizeType.Absolute, 38));
            outputPanel.RowStyles.Add(new RowStyle(SizeType.Absolute, 42));
            outputPanel.BackColor = PanelColor;
            layout.Controls.Add(outputPanel, 0, 8);

            outputBox = CreateTextBox(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "converted-audio"));
            outputPanel.Controls.Add(outputBox, 0, 0);

            var chooseOutput = CreateOutlineButton("选择输出目录");
            chooseOutput.Dock = DockStyle.Fill;
            chooseOutput.Margin = new Padding(0, 8, 0, 0);
            chooseOutput.Click += ChooseOutputDirectory;
            outputPanel.Controls.Add(chooseOutput, 0, 1);

            var ffmpegPanel = new TableLayoutPanel();
            ffmpegPanel.Dock = DockStyle.Top;
            ffmpegPanel.ColumnCount = 1;
            ffmpegPanel.RowCount = 4;
            ffmpegPanel.RowStyles.Add(new RowStyle(SizeType.Absolute, 30));
            ffmpegPanel.RowStyles.Add(new RowStyle(SizeType.Absolute, 38));
            ffmpegPanel.RowStyles.Add(new RowStyle(SizeType.Absolute, 44));
            ffmpegPanel.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            ffmpegPanel.BackColor = PanelColor;
            layout.Controls.Add(ffmpegPanel, 0, 9);

            ffmpegPanel.Controls.Add(CreateFieldLabel("ffmpeg 状态"), 0, 0);
            ffmpegBox = CreateTextBox(GetInitialFfmpegText());
            ffmpegPanel.Controls.Add(ffmpegBox, 0, 1);

            var chooseFfmpeg = CreateOutlineButton("选择备用 ffmpeg.exe");
            chooseFfmpeg.Dock = DockStyle.Top;
            chooseFfmpeg.Margin = new Padding(0, 8, 0, 0);
            chooseFfmpeg.Click += ChooseFfmpeg;
            ffmpegPanel.Controls.Add(chooseFfmpeg, 0, 2);

            var note = new Label();
            note.Text = "单文件版会自动使用内置 ffmpeg。";
            note.ForeColor = MutedColor;
            note.Dock = DockStyle.Top;
            note.Height = 46;
            note.Padding = new Padding(0, 10, 0, 0);
            ffmpegPanel.Controls.Add(note, 0, 3);

            return card;
        }

        private Control CreateFooter()
        {
            var panel = new CardPanel();
            panel.Dock = DockStyle.Fill;
            panel.Margin = new Padding(0, 18, 0, 0);
            panel.Padding = new Padding(16, 12, 16, 12);
            panel.BackColor = PanelColor;

            var layout = new TableLayoutPanel();
            layout.Dock = DockStyle.Fill;
            layout.ColumnCount = 5;
            layout.RowCount = 1;
            layout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 118));
            layout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 112));
            layout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 132));
            layout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 240));
            layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            layout.BackColor = PanelColor;
            panel.Controls.Add(layout);

            var addButton = CreateOutlineButton("添加文件");
            addButton.Click += ChooseFiles;
            layout.Controls.Add(addButton, 0, 0);

            clearButton = CreateOutlineButton("清空列表");
            clearButton.Click += delegate { files.Clear(); RefreshFileList(); };
            layout.Controls.Add(clearButton, 1, 0);

            convertButton = CreateAccentButton("开始转换");
            convertButton.Click += ConvertFiles;
            layout.Controls.Add(convertButton, 2, 0);

            progressBar = new ProgressBar();
            progressBar.Dock = DockStyle.Fill;
            progressBar.Margin = new Padding(14, 8, 14, 8);
            progressBar.Style = ProgressBarStyle.Continuous;
            layout.Controls.Add(progressBar, 3, 0);

            statusLabel = new Label();
            statusLabel.Dock = DockStyle.Fill;
            statusLabel.ForeColor = MutedColor;
            statusLabel.TextAlign = ContentAlignment.MiddleLeft;
            layout.Controls.Add(statusLabel, 4, 0);

            return panel;
        }

        private static Label CreateFieldLabel(string text)
        {
            var label = new Label();
            label.Text = text;
            label.Dock = DockStyle.Fill;
            label.ForeColor = MutedColor;
            label.Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Bold);
            label.TextAlign = ContentAlignment.BottomLeft;
            return label;
        }

        private static ComboBox CreateComboBox(object[] values, int selectedIndex)
        {
            var box = new ComboBox();
            box.Dock = DockStyle.Fill;
            box.DropDownStyle = ComboBoxStyle.DropDownList;
            box.FlatStyle = FlatStyle.Flat;
            box.Font = new Font("Microsoft YaHei UI", 10F, FontStyle.Regular);
            box.Items.AddRange(values);
            box.SelectedIndex = selectedIndex;
            return box;
        }

        private static TextBox CreateTextBox(string text)
        {
            var box = new TextBox();
            box.Dock = DockStyle.Fill;
            box.ReadOnly = true;
            box.BorderStyle = BorderStyle.FixedSingle;
            box.BackColor = Color.FromArgb(249, 251, 253);
            box.ForeColor = TextColor;
            box.Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular);
            box.Text = text;
            return box;
        }

        private static Button CreateOutlineButton(string text)
        {
            var button = new Button();
            button.Text = text;
            button.Dock = DockStyle.Fill;
            button.Margin = new Padding(0, 2, 10, 2);
            button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderColor = LineColor;
            button.FlatAppearance.MouseOverBackColor = Color.FromArgb(238, 244, 248);
            button.BackColor = Color.White;
            button.ForeColor = TextColor;
            button.Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Bold);
            return button;
        }

        private static Button CreateAccentButton(string text)
        {
            var button = new Button();
            button.Text = text;
            button.Dock = DockStyle.Fill;
            button.Margin = new Padding(0, 2, 10, 2);
            button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderColor = AccentColor;
            button.FlatAppearance.MouseOverBackColor = AccentHoverColor;
            button.BackColor = AccentColor;
            button.ForeColor = Color.White;
            button.Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Bold);
            return button;
        }

        private void DrawFileItem(object sender, DrawItemEventArgs e)
        {
            if (e.Index < 0) return;
            var selected = (e.State & DrawItemState.Selected) == DrawItemState.Selected;
            using (var background = new SolidBrush(selected ? Color.FromArgb(226, 243, 238) : fileList.BackColor))
            {
                e.Graphics.FillRectangle(background, e.Bounds);
            }

            var path = fileList.Items[e.Index].ToString();
            var name = Path.GetFileName(path);
            var folder = Path.GetDirectoryName(path) ?? string.Empty;

            using (var accent = new SolidBrush(WarmColor))
            using (var text = new SolidBrush(TextColor))
            using (var muted = new SolidBrush(MutedColor))
            using (var nameFont = new Font(Font.FontFamily, 9.5F, FontStyle.Bold))
            using (var folderFont = new Font(Font.FontFamily, 8F, FontStyle.Regular))
            {
                e.Graphics.FillEllipse(accent, e.Bounds.Left + 10, e.Bounds.Top + 9, 14, 14);
                e.Graphics.DrawString(name, nameFont, text, e.Bounds.Left + 34, e.Bounds.Top + 3);
                e.Graphics.DrawString(folder, folderFont, muted, e.Bounds.Left + 34, e.Bounds.Top + 19);
            }
        }

        private void ChooseFiles(object sender, EventArgs e)
        {
            using (var dialog = new OpenFileDialog())
            {
                dialog.Title = "选择音频文件";
                dialog.Filter = "音频文件|*.mp3;*.wav;*.flac;*.aac;*.m4a;*.ogg;*.opus;*.wma;*.webm|所有文件|*.*";
                dialog.Multiselect = true;
                if (dialog.ShowDialog(this) == DialogResult.OK)
                {
                    AddFiles(dialog.FileNames);
                }
            }
        }

        private void ChooseOutputDirectory(object sender, EventArgs e)
        {
            using (var dialog = new FolderBrowserDialog())
            {
                dialog.Description = "选择转换后的输出目录";
                if (Directory.Exists(outputBox.Text)) dialog.SelectedPath = outputBox.Text;
                if (dialog.ShowDialog(this) == DialogResult.OK) outputBox.Text = dialog.SelectedPath;
            }
        }

        private void ChooseFfmpeg(object sender, EventArgs e)
        {
            var embedded = GetBundledFfmpegPath();
            if (File.Exists(embedded))
            {
                ffmpegBox.Text = "已内置 ffmpeg";
                MessageBox.Show(this, "已使用内置 ffmpeg。", "ffmpeg", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            using (var dialog = new OpenFileDialog())
            {
                dialog.Title = "选择 ffmpeg.exe";
                dialog.Filter = "ffmpeg.exe|ffmpeg.exe|可执行文件|*.exe|所有文件|*.*";
                if (dialog.ShowDialog(this) == DialogResult.OK) ffmpegBox.Text = dialog.FileName;
            }
        }

        private void HandleDragEnter(object sender, DragEventArgs e)
        {
            if (e.Data.GetDataPresent(DataFormats.FileDrop)) e.Effect = DragDropEffects.Copy;
        }

        private void HandleDragDrop(object sender, DragEventArgs e)
        {
            var dropped = e.Data.GetData(DataFormats.FileDrop) as string[];
            if (dropped != null) AddFiles(dropped);
        }

        private void AddFiles(IEnumerable<string> paths)
        {
            foreach (var path in paths)
            {
                if (File.Exists(path) && !files.Contains(path)) files.Add(path);
            }
            RefreshFileList();
        }

        private void RefreshFileList()
        {
            fileList.Items.Clear();
            foreach (var file in files) fileList.Items.Add(file);
            countLabel.Text = files.Count + " 个文件";
            statusLabel.Text = files.Count == 0 ? "拖放音频文件到窗口，或点击添加文件。" : "已准备好转换 " + files.Count + " 个文件。";
            RefreshButtons();
        }

        private void RefreshButtons()
        {
            convertButton.Enabled = files.Count > 0;
            clearButton.Enabled = files.Count > 0;
        }

        private void ConvertFiles(object sender, EventArgs e)
        {
            if (files.Count == 0) return;

            var ffmpeg = GetBundledFfmpegPath();
            if (!File.Exists(ffmpeg)) ffmpeg = ffmpegBox.Text.Trim();
            if (!File.Exists(ffmpeg))
            {
                MessageBox.Show(this, "没有找到 ffmpeg。请使用单文件版，或点击“选择备用 ffmpeg.exe”指定路径。", "缺少 ffmpeg", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            Directory.CreateDirectory(outputBox.Text);
            ToggleWorking(true);
            progressBar.Minimum = 0;
            progressBar.Maximum = files.Count;
            progressBar.Value = 0;

            int success = 0;
            for (int index = 0; index < files.Count; index++)
            {
                var input = files[index];
                var output = CreateOutputPath(input);
                statusLabel.Text = "正在转换：" + Path.GetFileName(input);
                Application.DoEvents();

                if (RunFfmpeg(ffmpeg, input, output) == 0) success++;
                else MessageBox.Show(this, "转换失败：" + Path.GetFileName(input), "转换失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
                progressBar.Value = index + 1;
            }

            ToggleWorking(false);
            statusLabel.Text = "完成：成功转换 " + success + " / " + files.Count + " 个文件。";
            MessageBox.Show(this, "转换完成。\n输出目录：" + outputBox.Text, "完成", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        private string CreateOutputPath(string input)
        {
            var extension = formatBox.SelectedItem.ToString();
            var baseName = Path.GetFileNameWithoutExtension(input);
            var target = Path.Combine(outputBox.Text, baseName + "." + extension);
            int counter = 2;
            while (File.Exists(target))
            {
                target = Path.Combine(outputBox.Text, baseName + " (" + counter + ")." + extension);
                counter++;
            }
            return target;
        }

        private int RunFfmpeg(string ffmpeg, string input, string output)
        {
            var args = new StringBuilder();
            args.Append("-y -i ").Append(Quote(input)).Append(' ');
            AppendQualityArgs(args);
            args.Append(Quote(output));

            var startInfo = new ProcessStartInfo();
            startInfo.FileName = ffmpeg;
            startInfo.Arguments = args.ToString();
            startInfo.UseShellExecute = false;
            startInfo.CreateNoWindow = true;
            startInfo.RedirectStandardError = true;
            startInfo.RedirectStandardOutput = true;

            using (var process = Process.Start(startInfo))
            {
                process.WaitForExit();
                return process.ExitCode;
            }
        }

        private void AppendQualityArgs(StringBuilder args)
        {
            var format = formatBox.SelectedItem.ToString();
            var quality = qualityBox.SelectedItem.ToString();

            if (format == "mp3")
            {
                args.Append("-vn -codec:a libmp3lame ");
                AppendBitrate(args, quality);
            }
            else if (format == "aac" || format == "m4a")
            {
                args.Append("-vn -codec:a aac ");
                AppendBitrate(args, quality);
            }
            else if (format == "ogg")
            {
                args.Append("-vn -codec:a libvorbis ");
                AppendBitrate(args, quality);
            }
            else if (format == "flac") args.Append("-vn -codec:a flac ");
            else if (format == "wav") args.Append("-vn -codec:a pcm_s16le ");
        }

        private static void AppendBitrate(StringBuilder args, string quality)
        {
            if (quality.IndexOf("320", StringComparison.Ordinal) >= 0) args.Append("-b:a 320k ");
            else if (quality.IndexOf("128", StringComparison.Ordinal) >= 0) args.Append("-b:a 128k ");
            else if (quality.IndexOf("默认", StringComparison.Ordinal) < 0) args.Append("-b:a 192k ");
        }

        private static string Quote(string value)
        {
            return "\"" + value.Replace("\"", "\\\"") + "\"";
        }

        private string GetInitialFfmpegText()
        {
            return HasBundledFfmpeg() ? "已内置 ffmpeg" : FindFfmpeg();
        }

        private static bool HasBundledFfmpeg()
        {
            using (var stream = typeof(MainForm).Assembly.GetManifestResourceStream("AudioConverterApp.ffmpeg.exe"))
            {
                return stream != null;
            }
        }

        private string GetBundledFfmpegPath()
        {
            if (File.Exists(bundledFfmpegPath)) return bundledFfmpegPath;

            using (var stream = typeof(MainForm).Assembly.GetManifestResourceStream("AudioConverterApp.ffmpeg.exe"))
            {
                if (stream == null) return string.Empty;
                var version = typeof(MainForm).Assembly.GetName().Version.ToString();
                var directory = Path.Combine(Path.GetTempPath(), "AudioConverterApp", version);
                Directory.CreateDirectory(directory);
                bundledFfmpegPath = Path.Combine(directory, "ffmpeg.exe");
                if (!File.Exists(bundledFfmpegPath) || new FileInfo(bundledFfmpegPath).Length != stream.Length)
                {
                    using (var file = File.Create(bundledFfmpegPath)) stream.CopyTo(file);
                }
                return bundledFfmpegPath;
            }
        }

        private static string FindFfmpeg()
        {
            var local = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "ffmpeg.exe");
            if (File.Exists(local)) return local;

            var path = Environment.GetEnvironmentVariable("PATH") ?? string.Empty;
            foreach (var folder in path.Split(Path.PathSeparator))
            {
                try
                {
                    var candidate = Path.Combine(folder.Trim(), "ffmpeg.exe");
                    if (File.Exists(candidate)) return candidate;
                }
                catch
                {
                }
            }
            return string.Empty;
        }

        private void ToggleWorking(bool working)
        {
            convertButton.Enabled = !working && files.Count > 0;
            clearButton.Enabled = !working && files.Count > 0;
            formatBox.Enabled = !working;
            qualityBox.Enabled = !working;
        }
    }

    public sealed class CardPanel : Panel
    {
        public CardPanel()
        {
            DoubleBuffered = true;
            BackColor = Color.White;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            using (var path = RoundedRect(new Rectangle(0, 0, Width - 1, Height - 1), 14))
            using (var fill = new SolidBrush(BackColor))
            using (var pen = new Pen(Color.FromArgb(220, 226, 235)))
            {
                e.Graphics.FillPath(fill, path);
                e.Graphics.DrawPath(pen, path);
            }
        }

        protected override void OnResize(EventArgs eventargs)
        {
            base.OnResize(eventargs);
            Invalidate();
        }

        private static GraphicsPath RoundedRect(Rectangle bounds, int radius)
        {
            int diameter = radius * 2;
            var path = new GraphicsPath();
            path.AddArc(bounds.Left, bounds.Top, diameter, diameter, 180, 90);
            path.AddArc(bounds.Right - diameter, bounds.Top, diameter, diameter, 270, 90);
            path.AddArc(bounds.Right - diameter, bounds.Bottom - diameter, diameter, diameter, 0, 90);
            path.AddArc(bounds.Left, bounds.Bottom - diameter, diameter, diameter, 90, 90);
            path.CloseFigure();
            return path;
        }
    }
}
