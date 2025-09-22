# ERPDemo (Paketlenebilir Sürüm)

Bu sürüm, ERP demo uygulamasının **Windows için çalıştırılabilir EXE** olarak paketlenebilen halidir.  
Mail sistemi yoktur. Dışarıdan `application.properties` dosyası ile veritabanı ayarları yapılır.

---

## 1) Gereksinimler
- **Windows 10/11 (64-bit)**
- **SQL Server** (Express olabilir)
- **Java 21 JDK** (sadece uygulamayı derleyip paketlemek isteyen geliştiriciler için)
- **WiX Toolset 3.14** (sadece kurulumlu `.exe` yapmak isteyenler için)

Eğer sadece hazır **EXE** çalıştıracaksan → Java kurmana gerek yok.

---

## 2) Veritabanı Kurulumu
1. SQL Server Express kur. 
2. Yeni veritabanı oluştur: `SirketDB`
3. Aşağıdaki SQL’i çalıştır:

```sql
USE SirketDB;

CREATE TABLE Kullanicilar(
  Id INT IDENTITY(1,1) PRIMARY KEY,
  KullaniciAdi NVARCHAR(100),
  Sifre NVARCHAR(100),
  Rol NVARCHAR(50)
);

CREATE TABLE Musteriler(
  Id INT IDENTITY(1,1) PRIMARY KEY,
  FirmaAdi NVARCHAR(200),
  IletisimKisi NVARCHAR(200),
  Telefon NVARCHAR(50),
  Eposta NVARCHAR(200),
  Iskonto INT
);

CREATE TABLE Stoklar(
  Id INT IDENTITY(1,1) PRIMARY KEY,
  UrunAdi NVARCHAR(200),
  Fiyat DECIMAL(18,2),
  Stok INT,
  Birim NVARCHAR(20)
);

CREATE TABLE Talepler(
  Id INT IDENTITY(1,1) PRIMARY KEY,
  MusteriId INT,
  TalepTarihi DATE,
  Durum NVARCHAR(50)
);

CREATE TABLE TalepKalemleri(
  Id INT IDENTITY(1,1) PRIMARY KEY,
  TalepId INT,
  UrunId INT,
  Miktar INT,
  TeklifFiyati DECIMAL(18,2)
);

INSERT INTO Kullanicilar (KullaniciAdi, Sifre, Rol) VALUES
('admin', 'admin', 'Yonetici'),
('kullanici', '1234', 'Personel'); 

## 3) Uygulama Ayarları

Uygulamanın bulunduğu klasöre application.properties dosyası ekle:

db.url=jdbc:sqlserver://localhost;databaseName=SirketDB;encrypt=true;trustServerCertificate=true;
db.user=sa
db.password=Password1

(localhost, kullanıcı adı ve şifreyi kendi SQL Server kurulumuna göre değiştir.)

## 4) Çalıştırma

:: 0) Temiz derle
mvn -DskipTests=true clean package

:: 1) input klasörünü hazırlayıp jar + bağımlılıkları koy
rmdir /s /q target\app 2>nul
mkdir target\app
copy /Y target\ERPDemo-1.0-SNAPSHOT.jar target\app\

:: (Maven runtime bağımlılıklarını kopyala)
mvn -DskipTests=true dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\app

:: (JavaFX’in Windows sınıflı JAR’larını da ekle – Maven bunları otomatik koymuyor)
copy /Y "%USERPROFILE%\.m2\repository\org\openjfx\javafx-base\21\javafx-base-21-win.jar"       target\app\
copy /Y "%USERPROFILE%\.m2\repository\org\openjfx\javafx-graphics\21\javafx-graphics-21-win.jar" target\app\
copy /Y "%USERPROFILE%\.m2\repository\org\openjfx\javafx-controls\21\javafx-controls-21-win.jar" target\app\
copy /Y "%USERPROFILE%\.m2\repository\org\openjfx\javafx-fxml\21\javafx-fxml-21-win.jar"         target\app\

:: 2) Önceki app-image’ı sil
rmdir /s /q target\jpackage\Omnis 2>nul

:: 3) app-image (konsolsuz EXE’li klasör)
jpackage ^
  --type app-image ^
  --name Omnis ^
  --input target\app ^
  --main-jar ERPDemo-1.0-SNAPSHOT.jar ^
  --main-class com.example.erpdemo.HelloApplication ^
  --icon src\main\resources\com\example\erpdemo\assets\logo.ico ^
  --app-version 1.0.0 ^
  --vendor "Omnis" ^
  --copyright "© 2025 Omnis" ^
  --java-options "--module-path $APPDIR" ^
  --java-options "--add-modules=javafx.controls,javafx.fxml" ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --java-options "-Dprism.maxvram=512m" ^
  --dest target\jpackage

:: 4) dış ayar dosyan varsa (ör: db.properties) exe’nin yanına koy
copy /Y db.properties target\jpackage\Omnis\  2>nul

