#define MyAppName "可话"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Kehua"
#define MyAppExeName "Kehua.exe"

[Setup]
AppId={{B26D611B-A75D-4A6D-8C4F-34EA296A47C4}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\Programs\Kehua
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir=..\..\build\windows-installer
OutputBaseFilename=Kehua-Windows-Setup-x64
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加选项:"

[Files]
Source: "..\..\build\windows-publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "启动可话"; Flags: nowait postinstall skipifsilent
