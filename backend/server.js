import express from "express";
import cors from "cors";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const app = express();
app.use(cors());

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = process.env.PORT ? Number(process.env.PORT) : 8787;
const WORD_MS = process.env.WORD_MS ? Number(process.env.WORD_MS) : 50;

function loadText() {
  const p = path.join(__dirname, "text.txt");
  return fs.readFileSync(p, "utf8");
}

app.get("/", (_req, res) => {
  res.type("text/plain").send(
`ok
GET /sse   -> SSE stream (one word per ${WORD_MS}ms)
GET /text  -> plain HTTP full text
GET /health
`
  );
});

app.get("/health", (_req, res) => {
  res.json({ ok: true });
});

app.get("/text", (_req, res) => {
  res.type("text/plain").send(loadText());
});

app.get("/sse", (req, res) => {
  res.status(200);
  res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
  res.setHeader("Cache-Control", "no-cache, no-transform");
  res.setHeader("Connection", "keep-alive");
  res.setHeader("X-Accel-Buffering", "no");

  res.write(": connected\n\n");

  const words = loadText()
    .replace(/\s+/g, " ")
    .trim()
    .split(" ")
    .filter(Boolean);

  let i = 0;
  const timer = setInterval(() => {
    if (i >= words.length) {
      res.write("event: done\n");
      res.write("data: [DONE]\n\n");
      clearInterval(timer);
      res.end();
      return;
    }

    const word = words[i++];
    res.write("event: word\n");
    res.write(`data: ${word}\n\n`);
  }, WORD_MS);

  req.on("close", () => clearInterval(timer));
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`SSE backend running on http://0.0.0.0:${PORT}`);
  console.log(`Open: http://localhost:${PORT}/`);
});
