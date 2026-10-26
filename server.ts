import express from 'express';
import { createServer as createViteServer } from 'vite';
import path from 'path';
import dotenv from 'dotenv';
import { GoogleGenAI, Type } from '@google/genai';

dotenv.config();

const app = express();
const PORT = 3000;

app.use(express.json({ limit: '10mb' }));

function getGeminiClient(): GoogleGenAI {
  const apiKey = process.env.GEMINI_API_KEY;
  return new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        'User-Agent': 'aistudio-build',
      },
    },
  });
}

// POST /api/jarvis/intelligence
app.post('/api/jarvis/intelligence', async (req, res) => {
  try {
    const {
      utterance,
      userHonorific = 'Sir',
      selectedLanguage,
      voicePersona,
      activeForegroundApp,
      visibleScreenElements = [],
      intelligenceMode,
      grantedApps,
      deviceContext,
      conversationHistory = [],
    } = req.body;

    if (!utterance || typeof utterance !== 'string') {
      res.status(400).json({ error: 'Utterance is required' });
      return;
    }

    const ai = getGeminiClient();

    const personaInstructionMap: Record<string, string> = {
      deep_male:
        `Speak as JARVIS: Deep, calm, hyper-intelligent male persona with a clear Indian English or native Indian language accent. Address the user respectfully as "${userHonorific}".`,
      female_ai:
        `Speak as F.R.I.D.A.Y.: Articulate, warm, sharp female AI executive assistant. Address the user respectfully as "${userHonorific}".`,
      boy_energetic:
        `Speak as a sharp, energetic young tech prodigy boy—enthusiastic, fast, and crystal clear. Address the user as "${userHonorific}".`,
      girl_bright:
        `Speak as a cheerful, friendly, smart young girl assistant. Address the user as "${userHonorific}".`,
      soft_calm:
        `Speak in a soothing, soft, gentle, calm voice with reassuring clarity. Address the user as "${userHonorific}".`,
      funny_witty:
        `Speak with clever humor, witty punchlines, and relatable comedy while still giving 100% accurate, brilliant answers. Address the user as "${userHonorific}".`,
      chindi_tapori:
        `Speak in hilarious, street-smart Bambaiya/Chindi Tapori Hinglish style ("Aye bhidu", "Bole toh ekdum kadak", "Tension nahi lene ka") while still giving genuinely smart, accurate step-by-step solutions!`,
    };

    const selectedPersonaPrompt =
      personaInstructionMap[voicePersona || 'deep_male'] || personaInstructionMap.deep_male;

    const systemInstruction = `You are JARVIS ULTRA, combining the analytical depth of Claude AI, the multimodal speed of Google Gemini, and the conversational versatility of ChatGPT.

USER VOICE DETECTION & GREETING RULE:
- Based on the user's microphone voice frequency analysis, the user's honorific is "${userHonorific}" ("Sir" for male voice, "Ma'am" for female voice).
- Whenever the user says "Hey Jarvis" or "Jarvis" (as a wake greeting) or starts a fresh interaction, welcome them warmly with:
  "Hi ${userHonorific}, welcome to the JARVIS world, how can I help you?"
- If they also included a task/question after "Hey Jarvis", greet them briefly as "${userHonorific}" and immediately answer or guide them step-by-step.

ACTIVE VOICE PERSONA & STYLE:
${selectedPersonaPrompt}

LANGUAGE SETTING:
- Current preferred language: ${selectedLanguage || 'Auto-Detect (Indian English / Hindi / Hinglish)'}.
- Understand and reply naturally in whichever language or dialect the user speaks (Hindi, Hinglish, Chindi/Tapori slang, Indian English, or any of the 24 Indian languages).

LIVE SCREEN LINE NAVIGATION & STEP-BY-STEP TASK ENGINE:
- Currently open screen/app: "${activeForegroundApp}".
- Interactive UI elements currently visible on screen (with IDs): ${JSON.stringify(visibleScreenElements)}.
- If the user asks you to guide them step-by-step (e.g., "guide me to change display settings", "guide me step by step", "mujhe XYZ kaam karna hai step by step guide karo", "how to connect Wi-Fi", "show me where to tap"):
  1. Set "isStepByStepGuidance" to true.
  2. Populate "guidedTaskTitle" with a clear task name.
  3. Populate "guidedSteps" with 3 to 5 sequential steps. For each step, set "targetElementId" to match one of the visible screen element IDs if applicable (e.g., "nav_settings_display", "display_brightness", "display_dark_theme", "display_timeout", "wifi_network_5g", "wifi_connect_btn", "app_action_primary", "app_action_secondary", "app_action_export"), OR set "targetElementId" to "MANUAL_USER_SELECT" if the target is on an external/custom screen that cannot be automatically highlighted.

SPECIALIZED INTELLIGENCE CORES (Current focus: ${intelligenceMode || 'OMNI'}):
- Daily Life, Science, Coding, Math, Career, Finance, Strategy, Gaming Intelligence, Video/Photo Editing Intelligence, and Creator Growth Intelligence.
- Apps with user-granted access: ${JSON.stringify(grantedApps || [])}.
- Device state: ${JSON.stringify(deviceContext || {})}.`;

    const historyText = Array.isArray(conversationHistory)
      ? conversationHistory
          .slice(-6)
          .map((m: { role: string; text: string }) => `${m.role}: ${m.text}`)
          .join('\n')
      : '';

    const prompt = `${historyText ? `Recent conversation:\n${historyText}\n\n` : ''}User command: "${utterance}"`;

    const response = await ai.models.generateContent({
      model: 'gemini-3.8-flash',
      contents: prompt,
      config: {
        systemInstruction,
        responseMimeType: 'application/json',
        responseSchema: {
          type: Type.OBJECT,
          properties: {
            spokenResponse: {
              type: Type.STRING,
              description: 'Natural voice response matching the selected persona, language, and Sir/Ma\'am honorific.',
            },
            detectedLanguageLabel: {
              type: Type.STRING,
              description: 'Language/dialect used.',
            },
            newLanguageCode: {
              type: Type.STRING,
              description: 'BCP-47 code if user asked to switch language, otherwise empty string.',
            },
            intelligenceDomain: {
              type: Type.STRING,
              description: 'One of: LIVE_NAVIGATION | GAMING | EDITING | CREATOR | WORLD_OMNI | DEVICE_CONTROL',
            },
            targetAppId: {
              type: Type.STRING,
              description: 'One of: settings_display | settings_wifi | whatsapp | instagram | youtube | photos | gmail | gaming | editor | none',
            },
            requiresAppPermission: {
              type: Type.STRING,
              description: 'App ID requiring permission before access, or empty string.',
            },
            actionPerformed: {
              type: Type.STRING,
              description: 'Summary of action or guidance started.',
            },
            structuredCardTitle: {
              type: Type.STRING,
              description: 'Heading for the Dynamic Island card.',
            },
            structuredCardBullets: {
              type: Type.ARRAY,
              items: { type: Type.STRING },
              description: '2 to 4 concise insights or steps.',
            },
            isStepByStepGuidance: {
              type: Type.BOOLEAN,
              description: 'True if this request initiates or updates step-by-step live screen line navigation.',
            },
            guidedTaskTitle: {
              type: Type.STRING,
              description: 'Title of the guided task if isStepByStepGuidance is true.',
            },
            guidedSteps: {
              type: Type.ARRAY,
              items: {
                type: Type.OBJECT,
                properties: {
                  stepNumber: { type: Type.INTEGER },
                  shortInstruction: {
                    type: Type.STRING,
                    description: 'Short label shown on the neon screen badge.',
                  },
                  voiceInstruction: {
                    type: Type.STRING,
                    description: 'Spoken guidance for this step.',
                  },
                  targetElementId: {
                    type: Type.STRING,
                    description: 'Matching UI element ID on screen or MANUAL_USER_SELECT.',
                  },
                },
                required: ['stepNumber', 'shortInstruction', 'voiceInstruction', 'targetElementId'],
              },
            },
          },
          required: [
            'spokenResponse',
            'detectedLanguageLabel',
            'newLanguageCode',
            'intelligenceDomain',
            'targetAppId',
            'requiresAppPermission',
            'actionPerformed',
            'structuredCardTitle',
            'structuredCardBullets',
            'isStepByStepGuidance',
            'guidedTaskTitle',
            'guidedSteps',
          ],
        },
      },
    });

    const rawText = response.text || '{}';
    const parsed = JSON.parse(rawText);
    res.json(parsed);
  } catch (error: any) {
    console.error('Gemini intelligence error:', error);
    res.status(500).json({
      error: error?.message || 'Failed to process voice intelligence request',
    });
  }
});

// POST /api/jarvis/tts
app.post('/api/jarvis/tts', async (req, res) => {
  try {
    const { text, languageLabel, voicePersona } = req.body;
    if (!text || typeof text !== 'string') {
      res.status(400).json({ error: 'Text is required for TTS' });
      return;
    }

    const voiceMap: Record<string, { voiceName: string; style: string }> = {
      deep_male: {
        voiceName: 'Charon',
        style: `Deep, resonant, authoritative male voice with a clear Indian English accent (${languageLabel || 'India'})`,
      },
      female_ai: {
        voiceName: 'Kore',
        style: `Clear, articulate, intelligent female voice with natural Indian English cadence (${languageLabel || 'India'})`,
      },
      boy_energetic: {
        voiceName: 'Puck',
        style: `Energetic, bright young male voice (${languageLabel || 'India'})`,
      },
      girl_bright: {
        voiceName: 'Zephyr',
        style: `Cheerful, expressive young female voice (${languageLabel || 'India'})`,
      },
      soft_calm: {
        voiceName: 'Kore',
        style: `Soft, calm, soothing, gentle voice (${languageLabel || 'India'})`,
      },
      funny_witty: {
        voiceName: 'Puck',
        style: `Playful, witty, expressive comedic voice (${languageLabel || 'India'})`,
      },
      chindi_tapori: {
        voiceName: 'Fenrir',
        style: `Lively, street-smart Mumbai Tapori / Hinglish expressive male voice (${languageLabel || 'India'})`,
      },
    };

    const chosen = voiceMap[voicePersona || 'deep_male'] || voiceMap.deep_male;

    const ai = getGeminiClient();
    const response = await ai.models.generateContent({
      model: 'gemini-3.8-flash-lite-tts',
      contents: [
        {
          role: 'user',
          parts: [
            {
              text,
              speechMetadata: {
                style: chosen.style,
              },
            },
          ],
        },
      ],
      config: {
        responseModalities: ['AUDIO'],
        speechConfig: {
          voiceConfig: {
            prebuiltVoiceConfig: { voiceName: chosen.voiceName },
          },
        },
      },
    });

    const base64Audio =
      response.candidates?.[0]?.content?.parts?.[0]?.inlineData?.data;

    if (!base64Audio) {
      res.status(500).json({ error: 'No audio returned from TTS model' });
      return;
    }

    res.json({ audioBase64: base64Audio, mimeType: 'audio/wav' });
  } catch (error: any) {
    console.error('Gemini TTS error:', error);
    res.status(500).json({
      error: error?.message || 'TTS generation failed',
    });
  }
});

async function startServer() {
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`JARVIS ULTRA server running on http://localhost:${PORT}`);
  });
}

startServer();
