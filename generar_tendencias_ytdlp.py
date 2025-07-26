import json
import subprocess

YT_TRENDING_URL = "https://www.youtube.com/feed/trending"

# Ejecuta yt-dlp en modo JSON
process = subprocess.Popen(
    ["yt-dlp", "-j", "--flat-playlist", YT_TRENDING_URL],
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE
)

stdout, stderr = process.communicate()

if process.returncode != 0:
    print(json.dumps({"error": stderr.decode()}))
    exit(1)

# Procesa línea a línea
lines = stdout.decode().strip().split("\n")
resultados = []

for line in lines[:10]:  # Solo top 10
    video = json.loads(line)
    video_id = video.get("id")
    title = video.get("title")
    url = f"https://www.youtube.com/watch?v={video_id}"
    thumbnail = f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"

    resultados.append({
        "titulo": title,
        "videoId": video_id,
        "url": url,
        "imagen": thumbnail
    })

print(json.dumps(resultados, ensure_ascii=False, indent=2))
