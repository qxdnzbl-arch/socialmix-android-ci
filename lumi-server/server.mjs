import express from "express";
import OpenAI from "openai";

const app = express();
const port = Number(process.env.PORT || 10000);
const accessToken = process.env.APP_ACCESS_TOKEN || "";
const model = process.env.OPENAI_MODEL || "gpt-5.6-sol";

app.disable("x-powered-by");
app.use(express.json({ limit: "128kb" }));

app.get("/health", (_req, res) => {
  res.json({ ok: true, model, configured: Boolean(process.env.OPENAI_API_KEY) });
});

app.post("/chat", async (req, res) => {
  try {
    const auth = req.get("authorization") || "";
    if (!accessToken || auth !== `Bearer ${accessToken}`) {
      return res.status(401).json({ error: "未授权" });
    }
    if (!process.env.OPENAI_API_KEY) {
      return res.status(503).json({ error: "AI 连接还没有完成配置" });
    }

    const raw = Array.isArray(req.body?.messages) ? req.body.messages : [];
    const messages = raw
      .slice(-40)
      .map((m) => ({
        role: m?.role === "assistant" ? "assistant" : "user",
        content: String(m?.content || "").slice(0, 20000)
      }))
      .filter((m) => m.content.trim().length > 0);

    if (!messages.length) {
      return res.status(400).json({ error: "没有可发送的内容" });
    }

    const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
    const response = await client.responses.create({
      model,
      instructions: [
        "你是用户的私人 AI 助手。默认使用中文。",
        "回答高效、务实、直接，先给最有用的结果，不铺垫，不重复用户的问题。",
        "涉及操作时给清楚的当前步骤；涉及判断时说明关键原因。",
        "不要假装完成没有实际完成或验证的事情。"
      ].join("\n"),
      input: messages
    });

    const text = response.output_text?.trim();
    if (!text) return res.status(502).json({ error: "AI 没有返回文本" });
    res.json({ text });
  } catch (error) {
    console.error(error);
    const status = Number(error?.status) || 500;
    const safeStatus = status >= 400 && status < 600 ? status : 500;
    res.status(safeStatus).json({ error: "AI 请求失败，请稍后重试" });
  }
});

app.listen(port, "0.0.0.0", () => {
  console.log(`Lumi Chat server listening on ${port}`);
});
