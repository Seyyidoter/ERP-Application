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

Kurulumsuz (portable) versiyon → ERPDemo.exe’ye çift tıkla.

Kurulumlu versiyon → ERPDemo-1.0.0.exe ile kur, Başlat menüsünden aç.
