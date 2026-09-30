<div align="center">

<img src="https://capsule-render.vercel.app/api?type=rect&color=0:0b1220,100:0e7490&height=110&section=header&text=DamageIndicators&fontSize=42&fontColor=22d3ee&fontAlignY=54&desc=Damage%20numbers%20and%20health%20bars&descSize=13&descColor=94a3b8&descAlignY=80" width="100%" alt="DamageIndicators" />

<p>
<img src="https://img.shields.io/github/v/release/chizzar-dev/DamageIndicators?style=flat&label=release&color=06b6d4&labelColor=0b1220" alt="release" />
<img src="https://img.shields.io/badge/Minecraft-1.8%20%E2%80%93%201.21.11-0891b2?style=flat&labelColor=0b1220" alt="Minecraft 1.8 - 1.21.11" />
<img src="https://img.shields.io/badge/Java-8%2B-155e75?style=flat&labelColor=0b1220&logo=openjdk&logoColor=22d3ee" alt="Java 8+" />
<a href="LICENSE"><img src="https://img.shields.io/github/license/chizzar-dev/DamageIndicators?style=flat&label=license&color=0e7490&labelColor=0b1220" alt="license" /></a>
</p>

</div>

DamageIndicators shows floating damage numbers, health below player names and health bars above mobs. It uses armour stands, so no extra library is required.

*DamageIndicators, havada yukselen hasar sayilari, oyuncu isminin altinda can ve moblarin ustunde can bari gosterir. Armour stand kullanir, ek kutuphane gerektirmez.*

## Features · Özellikler
- Vuruşta havada yükselen **hasar göstergesi**
- Kritik vuruş, ölüm darbesi ve iyileşme için ayrı biçimler
- Oyuncuların **isminin altında can** (isteğe bağlı olarak TAB listesinde de)
- Mobların üstünde **can barı** — sayı (`20 ❤`) ya da bar (`❤❤❤❤❤`) olarak
- Cana göre renk değişimi: yeşil → sarı → kırmızı
- Hasar sebebine göre özel biçim, istenmeyen sebepleri gizleme
- **Spam koruması** — lav/ateş gibi sürekli hasarlarda gösterge yağmurunu engeller
- Dünya ve varlık türü bazında kapatma
- Hiçbir bağımlılığı yok, ProtocolLib gerektirmez

## Installation · Kurulum
1. [Releases](https://github.com/chizzar-dev/DamageIndicators/releases/latest) sayfasından `DamageIndicators.jar` dosyasını indir.
2. Sunucunun `plugins/` klasörüne at.
3. Sunucuyu yeniden başlat.
4. `plugins/DamageIndicators/config.yml` dosyasından biçimleri düzenle.

## Commands · Komutlar
| Komut | Açıklama | Yetki |
|-------|----------|-------|
| `/dmgindicators reload` | Ayarları yeniden yükler | `dmgindicators.reload` |

**Alias:** `/di` · `/dmg`

## Permissions · Yetkiler
| Yetki | Açıklama | Varsayılan |
|-------|----------|------------|
| `dmgindicators.reload` | Configi yeniden yükler | op |

## Configuration · Ayarlar
| Anahtar | Açıklama |
|---------|----------|
| `damage-indicator.format` | Normal hasar biçimi |
| `damage-indicator.crit-format` | Kritik vuruş biçimi |
| `damage-indicator.death-format` | Ölüm darbesi biçimi |
| `damage-indicator.decimals` | Ondalık basamak sayısı (0 = tam sayı) |
| `damage-indicator.duration-ticks` | Gösterge kaç tick durur |
| `damage-indicator.rise` | Yukarı süzülme miktarı (0 = sabit) |
| `damage-indicator.throttle-ms` | Aynı varlık için iki gösterge arası en az süre |
| `damage-indicator.ignored-causes` | Bu sebeplerde gösterge çıkmaz |
| `damage-indicator.causes` | Sebebe göre özel biçim |
| `player-health.enabled` / `symbol` | İsim altında can ve simgesi |
| `self-health.actionbar` | Kendi canını action bar'da göster |
| `health-bar.mode` | `NUMBER` ya da `BAR` |
| `health-bar.always` | Sürekli görünsün mü, yoksa vurunca mı |
| `health-bar.ignored` | Can barı gösterilmeyecek varlıklar |

### Yer tutucular

`%damage%` · `%current%` · `%max%` · `%percent%` · `%bar%`

## Notes · Notlar
- Göstergeler **ArmorStand** ile çizilir; hiçbir ek kütüphane gerekmez.
- 1.8'de yerçekimini kapatan API yoktur; bu yüzden göstergenin konumu her karede sabitlenir ve düşmez.
- ❤ gibi simgeler bazı 1.8 istemcilerinde kutu görünebilir. O durumda configden düz metinle değiştir.
- Mob can barı, mobun kendi ismini geçici olarak değiştirir ve süre dolunca **eski ismini geri yükler**.

## Building · Derleme
```bash
mvn clean package
```
Çıktı · Output: `target/DamageIndicators.jar`

Her push [GitHub Actions](https://github.com/chizzar-dev/DamageIndicators/actions/workflows/build.yml) ile derlenir; `v*` etiketli sürümler jar'la birlikte [Releases](https://github.com/chizzar-dev/DamageIndicators/releases) sayfasına eklenir.
<br><sub>Every push is built by GitHub Actions; tagged `v*` releases attach the jar.</sub>

## License · Lisans
[MIT](LICENSE) — istediğin gibi kullan, değiştir, dağıt · use, modify and distribute freely

<div align="center"><sub>chizzar-dev · Minecraft plugins for 1.8 – 1.21.11 · <a href="https://discord.gg/forges">Discord</a></sub></div>
