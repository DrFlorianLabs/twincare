from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
CAPTURES = ROOT / "captures"
SLIDES = ROOT / "slides"
SLIDES.mkdir(parents=True, exist_ok=True)

W, H = 1920, 1080
BLUE = "#2A93D5"
PALE_BLUE = "#EAF6FD"
INK = "#263B47"
MUTED = "#667985"
GREEN = "#156500"
WHITE = "#FFFFFF"

FONT_REG = "/System/Library/Fonts/Supplemental/Arial.ttf"
FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def gradient(c1, c2):
    a = Image.new("RGB", (W, H), c1)
    b = Image.new("RGB", (W, H), c2)
    mask = Image.new("L", (1, H))
    px = mask.load()
    for y in range(H):
        px[0, y] = int(255 * y / (H - 1))
    return Image.composite(b, a, mask.resize((W, H)))


def text(draw, xy, value, size, color=INK, bold=False, anchor=None):
    draw.text(xy, value, font=font(size, bold), fill=color, anchor=anchor)


def wrap(draw, value, max_width, size, bold=False):
    words, lines, current = value.split(), [], ""
    f = font(size, bold)
    for word in words:
        trial = f"{current} {word}".strip()
        if draw.textbbox((0, 0), trial, font=f)[2] <= max_width:
            current = trial
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def multiline(draw, xy, value, max_width, size, color=INK, bold=False, gap=1.18):
    x, y = xy
    for line in wrap(draw, value, max_width, size, bold):
        text(draw, (x, y), line, size, color, bold)
        y += int(size * gap)
    return y


def shadow_card(base, box, radius=34, shadow=24, fill=WHITE):
    x0, y0, x1, y1 = box
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle((x0, y0 + 12, x1, y1 + 12), radius, fill=(20, 50, 65, 72))
    layer = layer.filter(ImageFilter.GaussianBlur(shadow))
    base.alpha_composite(layer)
    d = ImageDraw.Draw(base)
    d.rounded_rectangle(box, radius, fill=fill)


def phone(base, screenshot_path, x=1325, y=72, screen_h=936):
    shot = Image.open(screenshot_path).convert("RGB")
    ratio = screen_h / shot.height
    screen_w = int(shot.width * ratio)
    outer = (x - 18, y - 18, x + screen_w + 18, y + screen_h + 18)
    shadow_card(base, outer, radius=54, shadow=28, fill="#101820")
    shot = shot.resize((screen_w, screen_h), Image.Resampling.LANCZOS)
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen_w, screen_h), 38, fill=255)
    base.paste(shot, (x, y), mask)
    d = ImageDraw.Draw(base)
    d.rounded_rectangle((x + screen_w // 2 - 48, y + 12, x + screen_w // 2 + 48, y + 24), 7, fill="#101820")


def browser(base, screenshot_path, box=(550, 130, 1840, 936)):
    x0, y0, x1, y1 = box
    shadow_card(base, (x0 - 10, y0 - 10, x1 + 10, y1 + 10), radius=28, shadow=28, fill="#DDE5E9")
    shot = Image.open(screenshot_path).convert("RGB")
    target_w, target_h = x1 - x0, y1 - y0 - 48
    shot = shot.resize((target_w, target_h), Image.Resampling.LANCZOS)
    d = ImageDraw.Draw(base)
    d.rounded_rectangle((x0, y0, x1, y1), 22, fill="#F4F7F8")
    for i, c in enumerate(("#FF5F57", "#FFBD2E", "#28C840")):
        d.ellipse((x0 + 22 + i * 30, y0 + 17, x0 + 38 + i * 30, y0 + 33), fill=c)
    base.paste(shot, (x0, y0 + 48))


def eyebrow(draw, label, x, y, color=BLUE):
    draw.rounded_rectangle((x, y, x + 250, y + 52), 26, fill=color)
    text(draw, (x + 125, y + 27), label.upper(), 22, WHITE, True, "mm")


def save(img, name):
    img.convert("RGB").save(SLIDES / name, quality=95)


def prepare_mobile_captures():
    """Reframe Chrome's 500 px minimum mobile viewport into the 430 px phone screen."""
    raw_dir = CAPTURES / "raw-mobile"
    for name in ("patient-documents", "patient-parcours", "patient-environnement"):
        source = Image.open(raw_dir / f"{name}-500.png").convert("RGB")
        reframed = source.resize((430, 932), Image.Resampling.LANCZOS)
        reframed.save(CAPTURES / f"{name}.png", quality=96)


def title_slide():
    img = gradient("#DDF1FC", "#F8FBFD").convert("RGBA")
    d = ImageDraw.Draw(img)
    d.ellipse((1330, -180, 2100, 590), fill="#BFE5F8")
    d.ellipse((-280, 730, 420, 1430), fill="#D7F0E0")
    text(d, (960, 315), "Twin Care", 112, BLUE, True, "mm")
    text(d, (960, 455), "Le lien simple entre le patient", 50, INK, True, "mm")
    text(d, (960, 520), "et son équipe de soins", 50, INK, True, "mm")
    d.rounded_rectangle((690, 625, 1230, 698), 37, fill=WHITE)
    text(d, (960, 663), "Un parcours mieux coordonné", 28, GREEN, True, "mm")
    save(img, "01-title.png")


def patient_slide(capture, name, headline, body, accent, footer="Simple  •  mobile  •  rassurant"):
    img = gradient("#F8FBFD", "#E7F5FC").convert("RGBA")
    d = ImageDraw.Draw(img)
    eyebrow(d, "Côté patient", 110, 128, BLUE)
    y = multiline(d, (110, 246), headline, 980, 70, INK, True, 1.08)
    y += 42
    multiline(d, (110, y), body, 950, 35, MUTED, False, 1.35)
    d.rounded_rectangle((110, 820, 850, 828), 4, fill=accent)
    text(d, (110, 866), footer, 26, accent, True)
    phone(img, CAPTURES / capture)
    save(img, name)


def bridge_slide():
    img = gradient("#EAF6FD", "#F4FAF6").convert("RGBA")
    d = ImageDraw.Draw(img)
    text(d, (960, 145), "Une information qui circule,", 64, INK, True, "mm")
    text(d, (960, 225), "sans perdre le fil", 64, BLUE, True, "mm")
    shadow_card(img, (175, 355, 795, 840), 34, 22, WHITE)
    shadow_card(img, (1125, 355, 1745, 840), 34, 22, WHITE)
    text(d, (485, 445), "PATIENT", 28, BLUE, True, "mm")
    text(d, (485, 570), "Dépose", 58, INK, True, "mm")
    text(d, (485, 655), "ses informations", 34, MUTED, False, "mm")
    text(d, (1435, 445), "PROFESSIONNELS", 28, GREEN, True, "mm")
    text(d, (1435, 570), "Coordonnent", 58, INK, True, "mm")
    text(d, (1435, 655), "et prennent le relais", 34, MUTED, False, "mm")
    d.line((820, 597, 1085, 597), fill=BLUE, width=10)
    d.polygon(((1085, 597), (1035, 565), (1035, 629)), fill=BLUE)
    text(d, (960, 945), "Un même parcours, deux expériences adaptées", 30, INK, True, "mm")
    save(img, "05-bridge.png")


def pro_slide(capture, name, label, headline, body, color):
    img = gradient("#F7FAFB", "#EAF4F7").convert("RGBA")
    d = ImageDraw.Draw(img)
    eyebrow(d, label, 80, 128, color)
    y = multiline(d, (80, 225), headline, 415, 58, INK, True, 1.08)
    y += 34
    multiline(d, (80, y), body, 415, 30, MUTED, False, 1.35)
    d.rounded_rectangle((80, 785, 425, 793), 4, fill=color)
    text(d, (80, 830), "Vue de démonstration", 22, color, True)
    text(d, (80, 865), "Profils 100 % fictifs", 20, MUTED)
    browser(img, CAPTURES / capture)
    save(img, name)


def end_slide():
    img = gradient("#263B47", "#16262F").convert("RGBA")
    d = ImageDraw.Draw(img)
    d.ellipse((1410, -260, 2110, 440), fill="#315663")
    d.ellipse((-300, 730, 420, 1450), fill="#173F35")
    text(d, (960, 325), "Twin Care", 104, WHITE, True, "mm")
    text(d, (960, 470), "Relier. Simplifier. Accompagner.", 52, "#9ED9F4", True, "mm")
    text(d, (960, 585), "Du domicile au cabinet, un parcours plus fluide.", 34, "#DCE8ED", False, "mm")
    d.rounded_rectangle((720, 695, 1200, 774), 40, fill=BLUE)
    text(d, (960, 735), "La santé, mieux coordonnée", 26, WHITE, True, "mm")
    save(img, "08-end.png")


def render_video():
    try:
        import imageio_ffmpeg
        ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    except Exception as exc:
        raise SystemExit(f"FFmpeg indisponible: {exc}")

    slides = [
        "01-title.png", "02-patient-documents.png", "03-patient-parcours.png",
        "04-patient-environnement.png", "05-bridge.png", "06-pro-assistant.png",
        "07-pro-doctor.png", "08-end.png"
    ]
    durations = [3.5, 4.2, 4.2, 4.2, 3.2, 4.2, 4.2, 3.5]
    transition = 0.5
    cmd = [ffmpeg, "-y"]
    for slide, duration in zip(slides, durations):
        cmd += ["-loop", "1", "-t", str(duration), "-i", str(SLIDES / slide)]

    filters = []
    for i in range(len(slides)):
        filters.append(f"[{i}:v]scale={W}:{H},fps=30,format=yuv420p,setsar=1[v{i}]")
    offsets = []
    running = durations[0] - transition
    offsets.append(running)
    for i in range(1, len(durations) - 1):
        running += durations[i] - transition
        offsets.append(running)
    last = "v0"
    transitions = ["fade", "slideleft", "slideleft", "fade", "wipeleft", "slideleft", "fade"]
    for i in range(1, len(slides)):
        out = f"x{i}"
        filters.append(
            f"[{last}][v{i}]xfade=transition={transitions[i-1]}:duration={transition}:offset={offsets[i-1]}[{out}]"
        )
        last = out
    cmd += [
        "-filter_complex", ";".join(filters), "-map", f"[{last}]",
        "-c:v", "libx264", "-preset", "medium", "-crf", "18",
        "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an",
        str(ROOT / "Twin-Care-pitch-28s.mp4")
    ]
    subprocess.run(cmd, check=True)


if __name__ == "__main__":
    prepare_mobile_captures()
    title_slide()
    patient_slide(
        "patient-documents.png", "02-patient-documents.png",
        "Partager ses documents en quelques secondes",
        "Une expérience mobile directe : photo, sélection du document, envoi.", BLUE
    )
    patient_slide(
        "patient-parcours.png", "03-patient-parcours.png",
        "Comprendre son parcours de santé",
        "Des étapes lisibles et accessibles pour rester acteur de son suivi.", "#7B61C8"
    )
    patient_slide(
        "patient-environnement.png", "04-patient-environnement.png",
        "Anticiper son environnement",
        "Une carte localisée pour visualiser les pollens et la qualité de l’air autour de soi.", "#E67E22",
        "Géolocalisation  •  pollens  •  qualité de l’air"
    )
    bridge_slide()
    pro_slide(
        "pro-assistant.png", "06-pro-assistant.png", "Équipe soignante",
        "Centraliser et préparer les dossiers",
        "Une file active claire pour suivre les patients et gérer les documents.", GREEN
    )
    pro_slide(
        "pro-doctor.png", "07-pro-doctor.png", "Médecin",
        "Consulter le bon dossier au bon moment",
        "Les éléments validés sont disponibles dans une salle d’attente structurée.", "#37474F"
    )
    end_slide()
    render_video()
    print(ROOT / "Twin-Care-pitch-28s.mp4")
