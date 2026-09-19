using System;
using System.Diagnostics;
using System.IO;
using System.Windows;
using Microsoft.Web.WebView2.Core;

namespace KehuaWindows;

public partial class MainWindow : Window
{
    private const string VirtualHost = "app.kehua.local";
    private const string ProductionHost = "nvwdtfnhsyfdopaxdylx.supabase.co";

    public MainWindow()
    {
        InitializeComponent();
        Loaded += OnLoaded;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        try
        {
            await WebView.EnsureCoreWebView2Async();
            WebView.CoreWebView2.Settings.AreDevToolsEnabled = false;
            WebView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = true;
            WebView.CoreWebView2.Settings.IsStatusBarEnabled = false;
            WebView.CoreWebView2.NewWindowRequested += (_, args) =>
            {
                args.Handled = true;
                if (Uri.TryCreate(args.Uri, UriKind.Absolute, out var target))
                    OpenExternal(target);
            };
            WebView.CoreWebView2.NavigationStarting += (_, args) =>
            {
                if (!Uri.TryCreate(args.Uri, UriKind.Absolute, out var target))
                    return;
                if (target.Host.Equals(VirtualHost, StringComparison.OrdinalIgnoreCase) ||
                    target.Host.Equals(ProductionHost, StringComparison.OrdinalIgnoreCase))
                    return;
                args.Cancel = true;
                OpenExternal(target);
            };

            var resourceDir = Path.Combine(AppContext.BaseDirectory, "Resources");
            var htmlPath = Path.Combine(resourceDir, "kehua.html");
            if (!File.Exists(htmlPath))
                throw new FileNotFoundException("缺少可话生产界面资源", htmlPath);

            WebView.CoreWebView2.SetVirtualHostNameToFolderMapping(
                VirtualHost,
                resourceDir,
                CoreWebView2HostResourceAccessKind.Allow);
            WebView.Source = new Uri($"https://{VirtualHost}/kehua.html");
        }
        catch (Exception ex)
        {
            MessageBox.Show($"可话启动失败：{ex.Message}", "可话", MessageBoxButton.OK, MessageBoxImage.Error);
            Close();
        }
    }

    private static void OpenExternal(Uri uri)
    {
        try
        {
            Process.Start(new ProcessStartInfo(uri.AbsoluteUri) { UseShellExecute = true });
        }
        catch
        {
        }
    }
}
