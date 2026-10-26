import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Mic,
  Volume2,
  Check,
  Copy,
  Download,
  Sparkles,
  Palette,
  Sun,
  Wifi,
  Moon,
  Clock,
  UserCheck,
} from 'lucide-react';
import {
  DeviceScreenContext,
  OrbVisualState,
  PermissionMatrix,
  ValidationScenario,
} from './types/jarvis';
import { ANDROID_SOURCE_FILES, INITIAL_VALIDATION_CHECKLIST } from './data/androidCodeRegistry';
import {
  INDIAN_LANGUAGES,
  INSTALLED_PHONE_APPS,
  InstalledPhoneApp,
} from './data/indianLanguages';
import {
  LiquidGlassDynamicIsland,
  DynamicIslandCardData,
  IslandCustomTheme,
} from './components/LiquidGlassDynamicIsland';
import {
  LiveScreenLineNavigationOverlay,
  GuidedStepItem,
} from './components/LiveScreenLineNavigationOverlay';

type WorkspaceSection = 'simulator' | 'customizer' | 'source' | 'validation';
type IntelligenceCoreMode = 'OMNI' | 'GAMING' | 'EDITING' | 'CREATOR';
type VoicePersonaId =
  | 'deep_male'
  | 'female_ai'
  | 'boy_energetic'
  | 'girl_bright'
  | 'soft_calm'
  | 'funny_witty'
  | 'chindi_tapori';

type PhoneScreenView =
  | 'SETTINGS_HOME'
  | 'SETTINGS_DISPLAY'
  | 'SETTINGS_WIFI'
  | 'INSTALLED_APP';

type DetectedUserHonorific = 'Sir' | "Ma'am";

interface VoicePersonaOption {
  id: VoicePersonaId;
  label: string;
  subtitle: string;
  pitch: number;
  rate: number;
}

const VOICE_PERSONAS: VoicePersonaOption[] = [
  {
    id: 'deep_male',
    label: 'Deep Male (JARVIS Classic Indian English)',
    subtitle: 'Resonant, authoritative deep male AI voice',
    pitch: 0.78,
    rate: 0.98,
  },
  {
    id: 'female_ai',
    label: 'Female (F.R.I.D.A.Y. Clear AI)',
    subtitle: 'Sharp, articulate female executive AI voice',
    pitch: 1.18,
    rate: 1.02,
  },
  {
    id: 'boy_energetic',
    label: 'Boy (Young Energetic Prodigy)',
    subtitle: 'Fast, enthusiastic young tech voice',
    pitch: 1.1,
    rate: 1.08,
  },
  {
    id: 'girl_bright',
    label: 'Girl (Bright Friendly Assistant)',
    subtitle: 'Cheerful, expressive young female voice',
    pitch: 1.28,
    rate: 1.05,
  },
  {
    id: 'soft_calm',
    label: 'Soft & Calm (Soothing Voice)',
    subtitle: 'Gentle, low-stress, whisper-smooth guidance',
    pitch: 0.95,
    rate: 0.91,
  },
  {
    id: 'funny_witty',
    label: 'Funny & Witty (Comedy Buddy)',
    subtitle: 'Smart answers with Desi humor & punchlines',
    pitch: 1.14,
    rate: 1.06,
  },
  {
    id: 'chindi_tapori',
    label: 'Chindi / Bambaiya Tapori ("Apun ka Jarvis")',
    subtitle: 'Full street-smart Mumbaiya slang ("Bole toh ekdum kadak!")',
    pitch: 0.88,
    rate: 1.06,
  },
];

const DYNAMIC_ISLAND_THEMES: IslandCustomTheme[] = [
  {
    id: 'cyan',
    name: 'Neon Cyan (Classic Hologram)',
    primaryHex: '#00F5D4',
    secondaryHex: '#3B82F6',
    pillScale: 'standard',
    glassBlurPx: 28,
  },
  {
    id: 'purple',
    name: 'Electric Purple (Quantum Pulse)',
    primaryHex: '#A855F7',
    secondaryHex: '#EC4899',
    pillScale: 'standard',
    glassBlurPx: 28,
  },
  {
    id: 'gold',
    name: 'Solar Gold & Crimson',
    primaryHex: '#F59E0B',
    secondaryHex: '#EF4444',
    pillScale: 'wide',
    glassBlurPx: 30,
  },
  {
    id: 'emerald',
    name: 'Matrix Emerald (Tactical HUD)',
    primaryHex: '#10B981',
    secondaryHex: '#06B6D4',
    pillScale: 'compact',
    glassBlurPx: 26,
  },
];

const BUILT_IN_GUIDED_TASKS: Record<
  string,
  { title: string; initialView: PhoneScreenView; steps: GuidedStepItem[] }
> = {
  display_settings: {
    title: 'Opening Settings · Change Display',
    initialView: 'SETTINGS_HOME',
    steps: [
      {
        stepNumber: 1,
        shortInstruction: 'Tap Display Settings',
        voiceInstruction:
          'Step 1: I have opened Android Settings. Follow the glowing neon line and tap Display.',
        targetElementId: 'nav_settings_display',
      },
      {
        stepNumber: 2,
        shortInstruction: 'Adjust Brightness Level',
        voiceInstruction:
          'Step 2: Great. Now tap Brightness Level to boost screen clarity to 90%.',
        targetElementId: 'display_brightness',
      },
      {
        stepNumber: 3,
        shortInstruction: 'Enable AMOLED Dark Theme',
        voiceInstruction:
          'Step 3: Finally, tap AMOLED Dark Theme to save battery and complete your display setup.',
        targetElementId: 'display_dark_theme',
      },
    ],
  },
  wifi_settings: {
    title: 'Wi-Fi & 5G Network Setup',
    initialView: 'SETTINGS_HOME',
    steps: [
      {
        stepNumber: 1,
        shortInstruction: 'Open Wi-Fi & Network',
        voiceInstruction:
          'Step 1: Tap Network & Internet on your Settings screen.',
        targetElementId: 'nav_settings_wifi',
      },
      {
        stepNumber: 2,
        shortInstruction: 'Select Stark_5G_Ultra Network',
        voiceInstruction:
          'Step 2: Tap Stark_5G_Ultra (Low-Latency 6GHz band) from the network list.',
        targetElementId: 'wifi_network_5g',
      },
      {
        stepNumber: 3,
        shortInstruction: 'Tap Connect Safely',
        voiceInstruction:
          'Step 3: Tap Verify & Connect. Remember, JARVIS never auto-connects to networks without your tap.',
        targetElementId: 'wifi_connect_btn',
      },
    ],
  },
  app_workflow: {
    title: 'App Feature Step-by-Step Guide',
    initialView: 'INSTALLED_APP',
    steps: [
      {
        stepNumber: 1,
        shortInstruction: 'Tap Primary AI Boost Control',
        voiceInstruction:
          'Step 1: Follow the live neon line and tap the Primary Action button inside your active app.',
        targetElementId: 'app_action_primary',
      },
      {
        stepNumber: 2,
        shortInstruction: 'Configure Pro Preset',
        voiceInstruction:
          'Step 2: Now tap the Secondary Preset button to calibrate optimal parameters.',
        targetElementId: 'app_action_secondary',
      },
      {
        stepNumber: 3,
        shortInstruction: 'Apply & Export Changes',
        voiceInstruction:
          'Step 3: Tap Apply & Finalize to complete the workflow.',
        targetElementId: 'app_action_export',
      },
    ],
  },
  unidentified_fallback_demo: {
    title: 'Custom Canvas Target Check',
    initialView: 'INSTALLED_APP',
    steps: [
      {
        stepNumber: 1,
        shortInstruction: 'Locate Custom Game HUD Button',
        voiceInstruction:
          'Step 1: This element is rendered inside a custom OpenGL canvas without accessibility bounds. Please tap the target manually.',
        targetElementId: 'MANUAL_USER_SELECT',
      },
    ],
  },
};

export default function App() {
  const [activeSection, setActiveSection] = useState<WorkspaceSection>('simulator');

  // User Voice Pitch Detection State ("Sir" vs "Ma'am")
  const [userHonorific, setUserHonorific] = useState<DetectedUserHonorific>('Sir');
  const [detectedUserPitchHz, setDetectedUserPitchHz] = useState<number>(128);

  // Language, Voice Persona & Dynamic Island Custom Theme
  const [selectedLanguageCode, setSelectedLanguageCode] = useState<string>('en-IN');
  const [voicePersona, setVoicePersona] = useState<VoicePersonaId>('deep_male');
  const [customTheme, setCustomTheme] = useState<IslandCustomTheme>(DYNAMIC_ISLAND_THEMES[0]);
  const [intelligenceMode, setIntelligenceMode] = useState<IntelligenceCoreMode>('OMNI');
  const [voiceOutputEnabled, setVoiceOutputEnabled] = useState<boolean>(true);

  // Android OS Permissions & Accessibility State
  const [permissions] = useState<PermissionMatrix>({
    recordAudio: true,
    postNotifications: true,
    systemAlertWindow: true,
    readContacts: true,
    batteryOptimizationIgnored: true,
  });
  const [accessibilityEnabled] = useState<boolean>(true);

  const [grantedApps, setGrantedApps] = useState<Record<string, boolean>>({
    gaming: true,
    editor: true,
    instagram: true,
    youtube: true,
    whatsapp: true,
    photos: true,
    gmail: true,
  });

  // Phone Screen View & Interactive Settings State
  const [phoneScreenView, setPhoneScreenView] = useState<PhoneScreenView>('SETTINGS_HOME');
  const [activePhoneAppId, setActivePhoneAppId] = useState<InstalledPhoneApp['id']>('gaming');
  const [, setScreenContext] = useState<DeviceScreenContext>(
    DeviceScreenContext.OTHER_APP_FOREGROUND
  );
  const [displayBrightness, setDisplayBrightness] = useState<number>(72);
  const [amoledDarkTheme, setAmoledDarkTheme] = useState<boolean>(true);
  const [screenTimeoutSec, setScreenTimeoutSec] = useState<number>(60);
  const [wifiConnectedSsid, setWifiConnectedSsid] = useState<string>('Home_WiFi_2.4G');

  // Live Line Navigation Task Engine State
  const [activeTaskTitle, setActiveTaskTitle] = useState<string | null>(null);
  const [guidedSteps, setGuidedSteps] = useState<GuidedStepItem[]>([]);
  const [currentStepIdx, setCurrentStepIdx] = useState<number>(0);
  const [isGuidancePaused, setIsGuidancePaused] = useState<boolean>(false);

  // Dynamic Island State
  const [orbState, setOrbState] = useState<OrbVisualState>(OrbVisualState.LISTENING);
  const [isIslandExpanded, setIsIslandExpanded] = useState<boolean>(true);
  const [userTranscript, setUserTranscript] = useState<string>('Hey Jarvis');
  const [jarvisResponse, setJarvisResponse] = useState<string>(
    'Hi Sir, welcome to the JARVIS world, how can I help you?'
  );
  const [islandCardData, setIslandCardData] = useState<DynamicIslandCardData | null>({
    domain: 'JARVIS ULTRA · OMNI AI',
    languageLabel: 'Indian English (Deep Male)',
    actionPerformed: 'Voice Recognition & Live Line Navigation Ready',
    structuredTitle: 'Welcome to the JARVIS World',
    structuredBullets: [
      'Say "Hey Jarvis" — Automatically detects your voice (Sir / Ma\'am) and welcomes you',
      'Say "JARVIS, guide me to change display settings" for Live Line Navigation',
    ],
  });

  const [, setPendingPermissionCommand] = useState<string | null>(null);
  const [idleCountdown, setIdleCountdown] = useState<number | null>(null);

  // Continuous Voice Recognition & Waveform
  const [continuousVoiceActive, setContinuousVoiceActive] = useState<boolean>(false);
  const [voiceStatusText, setVoiceStatusText] = useState<string>(
    'Always-Ready Voice Recognition Standby — Say "Hey Jarvis" anytime'
  );
  const [waveformBars, setWaveformBars] = useState<number[]>([30, 58, 42, 76, 48, 64, 34]);
  const [spokenInputBox, setSpokenInputBox] = useState<string>('');
  const [conversationHistory, setConversationHistory] = useState<
    Array<{ role: string; text: string }>
  >([]);

  const [selectedSourceId, setSelectedSourceId] = useState<string>(ANDROID_SOURCE_FILES[0].id);
  const [copiedSourceId, setCopiedSourceId] = useState<string | null>(null);
  const [checklist, setChecklist] = useState<ValidationScenario[]>(INITIAL_VALIDATION_CHECKLIST);

  const phoneViewportRef = useRef<HTMLDivElement | null>(null);
  const idleTimerRef = useRef<number | null>(null);
  const speechRecognitionRef = useRef<any>(null);
  const activeAudioRef = useRef<HTMLAudioElement | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const pitchIntervalRef = useRef<number | null>(null);
  const latestHonorificRef = useRef<DetectedUserHonorific>(userHonorific);

  useEffect(() => {
    latestHonorificRef.current = userHonorific;
  }, [userHonorific]);

  const currentLanguageObj =
    INDIAN_LANGUAGES.find((l) => l.code === selectedLanguageCode) || INDIAN_LANGUAGES[0];
  const currentPersonaObj =
    VOICE_PERSONAS.find((p) => p.id === voicePersona) || VOICE_PERSONAS[0];
  const currentForegroundApp =
    INSTALLED_PHONE_APPS.find((a) => a.id === activePhoneAppId) || INSTALLED_PHONE_APPS[0];

  const clearIdleTimer = useCallback(() => {
    if (idleTimerRef.current !== null) {
      window.clearInterval(idleTimerRef.current);
      idleTimerRef.current = null;
    }
    setIdleCountdown(null);
  }, []);

  // Waveform animation
  useEffect(() => {
    const interval = window.setInterval(() => {
      const intensity =
        orbState === OrbVisualState.SPEAKING
          ? 92
          : activeTaskTitle || continuousVoiceActive
          ? 75
          : 36;
      setWaveformBars(
        Array.from({ length: 7 }, () => Math.max(20, Math.round(Math.random() * intensity)))
      );
    }, 160);
    return () => window.clearInterval(interval);
  }, [orbState, activeTaskTitle, continuousVoiceActive]);

  // Multi-Persona Voice Synthesis (Gemini Neural TTS + Tuned Browser Voice Fallback)
  const speakJarvisVoice = useCallback(
    async (text: string, onComplete?: () => void) => {
      if (!voiceOutputEnabled || !text) {
        onComplete?.();
        return;
      }

      if (activeAudioRef.current) {
        activeAudioRef.current.pause();
        activeAudioRef.current = null;
      }
      if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
        window.speechSynthesis.cancel();
      }

      setOrbState(OrbVisualState.SPEAKING);

      const speakBrowserFallback = () => {
        if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
          onComplete?.();
          return;
        }
        try {
          const utter = new SpeechSynthesisUtterance(text);
          const voices = window.speechSynthesis.getVoices();
          const bcp47 =
            selectedLanguageCode === 'hi-Latn' ? 'en-IN' : selectedLanguageCode;

          const wantFemale =
            voicePersona === 'female_ai' || voicePersona === 'girl_bright';

          const matchedVoice =
            voices.find(
              (v) =>
                v.lang.toLowerCase().includes(bcp47.toLowerCase()) &&
                (wantFemale
                  ? /female|veena|lekha|heera|swara|google/i.test(v.name)
                  : /male|rishi|prabhat|hemant|madhur/i.test(v.name))
            ) ||
            voices.find((v) => v.lang.toLowerCase().includes(bcp47.toLowerCase())) ||
            voices.find((v) => v.lang.toLowerCase().includes('en-in'));

          if (matchedVoice) {
            utter.voice = matchedVoice;
            utter.lang = matchedVoice.lang;
          } else {
            utter.lang = bcp47;
          }

          utter.pitch = currentPersonaObj.pitch;
          utter.rate = currentPersonaObj.rate;
          utter.onend = () => {
            setOrbState(OrbVisualState.LISTENING);
            onComplete?.();
          };
          utter.onerror = () => {
            setOrbState(OrbVisualState.LISTENING);
            onComplete?.();
          };
          window.speechSynthesis.speak(utter);
        } catch {
          setOrbState(OrbVisualState.LISTENING);
          onComplete?.();
        }
      };

      try {
        const resp = await fetch('/api/jarvis/tts', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            text,
            languageLabel: currentLanguageObj.name,
            voicePersona,
          }),
        });
        if (resp.ok) {
          const data = await resp.json();
          if (data.audioBase64) {
            const audio = new Audio(`data:audio/wav;base64,${data.audioBase64}`);
            activeAudioRef.current = audio;
            audio.onended = () => {
              activeAudioRef.current = null;
              setOrbState(OrbVisualState.LISTENING);
              onComplete?.();
            };
            audio.onerror = () => {
              activeAudioRef.current = null;
              speakBrowserFallback();
            };
            await audio.play();
            return;
          }
        }
      } catch {
        // Fallback to browser voice
      }

      speakBrowserFallback();
    },
    [
      currentLanguageObj.name,
      currentPersonaObj.pitch,
      currentPersonaObj.rate,
      selectedLanguageCode,
      voiceOutputEnabled,
      voicePersona,
    ]
  );

  // Start a Step-by-Step Guided Task with Live Line Navigation
  const startGuidedTask = useCallback(
    (
      title: string,
      steps: GuidedStepItem[],
      initialScreen?: PhoneScreenView
    ) => {
      if (initialScreen) {
        setPhoneScreenView(initialScreen);
      }
      setScreenContext(DeviceScreenContext.OTHER_APP_FOREGROUND);
      setIsIslandExpanded(true);
      setActiveTaskTitle(title);
      setGuidedSteps(steps);
      setCurrentStepIdx(0);
      setIsGuidancePaused(false);

      const firstStep = steps[0];
      if (firstStep) {
        setJarvisResponse(firstStep.voiceInstruction);
        speakJarvisVoice(firstStep.voiceInstruction);
      }
    },
    [speakJarvisVoice]
  );

  // Advance to the next step in Live Line Navigation
  const handleNextStep = useCallback(() => {
    if (!activeTaskTitle || guidedSteps.length === 0) return;
    if (currentStepIdx + 1 < guidedSteps.length) {
      const nextIdx = currentStepIdx + 1;
      setCurrentStepIdx(nextIdx);
      const nextStepObj = guidedSteps[nextIdx];

      if (nextStepObj.targetElementId.startsWith('display_')) {
        setPhoneScreenView('SETTINGS_DISPLAY');
      } else if (nextStepObj.targetElementId.startsWith('wifi_')) {
        setPhoneScreenView('SETTINGS_WIFI');
      }

      setJarvisResponse(nextStepObj.voiceInstruction);
      speakJarvisVoice(nextStepObj.voiceInstruction);
    } else {
      const doneMsg = `Task completed, ${latestHonorificRef.current}! We finished all ${guidedSteps.length} steps of "${activeTaskTitle}".`;
      setActiveTaskTitle(null);
      setGuidedSteps([]);
      setCurrentStepIdx(0);
      setJarvisResponse(doneMsg);
      speakJarvisVoice(doneMsg);
    }
  }, [activeTaskTitle, currentStepIdx, guidedSteps, speakJarvisVoice]);

  const handlePreviousStep = useCallback(() => {
    if (!activeTaskTitle || currentStepIdx <= 0) return;
    const prevIdx = currentStepIdx - 1;
    setCurrentStepIdx(prevIdx);
    const prevStepObj = guidedSteps[prevIdx];
    if (
      prevStepObj.targetElementId === 'nav_settings_display' ||
      prevStepObj.targetElementId === 'nav_settings_wifi'
    ) {
      setPhoneScreenView('SETTINGS_HOME');
    }
    setJarvisResponse(prevStepObj.voiceInstruction);
    speakJarvisVoice(prevStepObj.voiceInstruction);
  }, [activeTaskTitle, currentStepIdx, guidedSteps, speakJarvisVoice]);

  const handlePauseResumeGuidance = useCallback(() => {
    if (!activeTaskTitle) return;
    setIsGuidancePaused((prev) => {
      const next = !prev;
      const msg = next
        ? `Live line navigation paused, ${latestHonorificRef.current}. Say "Resume navigation" when ready.`
        : guidedSteps[currentStepIdx]?.voiceInstruction || 'Resuming guidance.';
      setJarvisResponse(msg);
      speakJarvisVoice(msg);
      return next;
    });
  }, [activeTaskTitle, currentStepIdx, guidedSteps, speakJarvisVoice]);

  const handleRepeatStepInstruction = useCallback(() => {
    if (!activeTaskTitle) return;
    const step = guidedSteps[currentStepIdx];
    if (step) {
      setJarvisResponse(step.voiceInstruction);
      speakJarvisVoice(step.voiceInstruction);
    }
  }, [activeTaskTitle, currentStepIdx, guidedSteps, speakJarvisVoice]);

  const handleStopGuidance = useCallback(() => {
    setActiveTaskTitle(null);
    setGuidedSteps([]);
    setCurrentStepIdx(0);
    setIsGuidancePaused(false);
    const stopMsg = `Stopped live screen line navigation, ${latestHonorificRef.current}.`;
    setJarvisResponse(stopMsg);
    speakJarvisVoice(stopMsg);
  }, [speakJarvisVoice]);

  const explainVisibleScreen = useCallback(() => {
    let summary = '';
    if (phoneScreenView === 'SETTINGS_HOME') {
      summary = `On your screen right now, ${latestHonorificRef.current}: Android Settings Home with options for Display Settings, Wi-Fi & 5G Network, and App Launcher. Say "JARVIS, guide me to change display settings" to start live line navigation.`;
    } else if (phoneScreenView === 'SETTINGS_DISPLAY') {
      summary = `On your screen, ${latestHonorificRef.current}: Display Settings. Current Brightness is ${displayBrightness}%, AMOLED Dark Theme is ${
        amoledDarkTheme ? 'ON' : 'OFF'
      }, and Screen Timeout is ${screenTimeoutSec} seconds.`;
    } else if (phoneScreenView === 'SETTINGS_WIFI') {
      summary = `On your screen, ${latestHonorificRef.current}: Wi-Fi & Network Settings. Currently connected to ${wifiConnectedSsid}. Stark_5G_Ultra (6GHz Low-Latency) is available to select.`;
    } else {
      summary = `On your screen, ${latestHonorificRef.current}: ${currentForegroundApp.name} (${currentForegroundApp.subtitle}). Visible controls: Primary AI Boost, Pro Preset Calibrator, and Export/Apply.`;
    }

    setIsIslandExpanded(true);
    setJarvisResponse(summary);
    speakJarvisVoice(summary);
  }, [
    amoledDarkTheme,
    currentForegroundApp.name,
    currentForegroundApp.subtitle,
    displayBrightness,
    phoneScreenView,
    screenTimeoutSec,
    speakJarvisVoice,
    wifiConnectedSsid,
  ]);

  // Trigger Wake Greeting: "Hi Sir/Ma'am, welcome to the JARVIS world, how can I help you?"
  const triggerHeyJarvisWakeWelcome = useCallback(
    (honorificOverride?: DetectedUserHonorific) => {
      const honorific = honorificOverride || latestHonorificRef.current;
      const welcomeText = `Hi ${honorific}, welcome to the JARVIS world, how can I help you?`;
      setIsIslandExpanded(true);
      setUserTranscript('Hey Jarvis');
      setJarvisResponse(welcomeText);
      setIslandCardData({
        domain: 'WAKE WORD GREETING',
        languageLabel: `${currentLanguageObj.name} · Addressed as ${honorific}`,
        actionPerformed: `Welcomed ${honorific} (${detectedUserPitchHz}Hz voice profile)`,
        structuredTitle: `Hi ${honorific}, Welcome to the JARVIS World`,
        structuredBullets: [
          'Ask any daily life, coding, science, finance, gaming, or creator question',
          'Say "JARVIS, guide me to change display settings" for Live Line Navigation',
        ],
      });
      speakJarvisVoice(welcomeText);
    },
    [currentLanguageObj.name, detectedUserPitchHz, speakJarvisVoice]
  );

  // Process any Voice Command
  const processJarvisVoiceCommand = useCallback(
    async (rawUtterance: string, overrideGrantedApps?: Record<string, boolean>) => {
      const cleaned = rawUtterance.trim();
      if (!cleaned) return;

      setIsIslandExpanded(true);
      setUserTranscript(cleaned);

      // Check if user just said "Hey Jarvis" or "Jarvis" (Wake Greeting)
      const normalizedFull = cleaned.toLowerCase().replace(/[!.,?]/g, '').trim();
      if (
        normalizedFull === 'hey jarvis' ||
        normalizedFull === 'jarvis' ||
        normalizedFull === 'hi jarvis' ||
        normalizedFull === 'hello jarvis' ||
        normalizedFull === 'हे जार्विस' ||
        normalizedFull === 'जार्विस'
      ) {
        triggerHeyJarvisWakeWelcome();
        return;
      }

      const lower = cleaned
        .toLowerCase()
        .replace(/^hey\s+jarvis[,.\s]*/i, '')
        .replace(/^jarvis[,.\s]*/i, '')
        .trim();

      // 1. Check Voice-Controlled Navigation Transport Commands
      if (lower === 'next step' || lower === 'next' || lower === 'aage badho') {
        handleNextStep();
        return;
      }
      if (lower === 'previous step' || lower === 'go back' || lower === 'piche jao') {
        handlePreviousStep();
        return;
      }
      if (lower.includes('repeat') || lower.includes('dobara bolo')) {
        handleRepeatStepInstruction();
        return;
      }
      if (lower.includes('pause navigation') || lower === 'pause guidance') {
        if (!isGuidancePaused) handlePauseResumeGuidance();
        return;
      }
      if (lower.includes('resume navigation') || lower === 'resume guidance') {
        if (isGuidancePaused) handlePauseResumeGuidance();
        return;
      }
      if (lower.includes('stop guidance') || lower.includes('stop navigation')) {
        handleStopGuidance();
        return;
      }
      if (
        lower.includes('explain what is on my screen') ||
        lower.includes('what is on my screen') ||
        lower.includes('screen par kya hai')
      ) {
        explainVisibleScreen();
        return;
      }

      // 2. Check Built-In Verified Step-by-Step Live Line Navigation Demonstrations
      if (
        lower.includes('guide me to change display') ||
        lower.includes('display settings guide') ||
        lower.includes('display setting')
      ) {
        const task = BUILT_IN_GUIDED_TASKS.display_settings;
        startGuidedTask(task.title, task.steps, task.initialView);
        return;
      }

      if (
        lower.includes('guide me to wifi') ||
        lower.includes('guide me to wi-fi') ||
        lower.includes('connect wifi step by step')
      ) {
        const task = BUILT_IN_GUIDED_TASKS.wifi_settings;
        startGuidedTask(task.title, task.steps, task.initialView);
        return;
      }

      if (lower.includes('show me where to tap')) {
        if (activeTaskTitle) {
          handleRepeatStepInstruction();
        } else {
          const task = BUILT_IN_GUIDED_TASKS.display_settings;
          startGuidedTask(task.title, task.steps, task.initialView);
        }
        return;
      }

      // 3. Route to Server-Side Gemini 3.8 Flash Omni Intelligence
      setOrbState(OrbVisualState.THINKING);
      const effectiveGrantedMap = overrideGrantedApps || grantedApps;
      const allowedAppList = Object.entries(effectiveGrantedMap)
        .filter(([, allowed]) => allowed)
        .map(([k]) => k);

      try {
        const response = await fetch('/api/jarvis/intelligence', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            utterance: cleaned,
            userHonorific: latestHonorificRef.current,
            selectedLanguage: `${currentLanguageObj.name} (${currentLanguageObj.code})`,
            voicePersona,
            activeForegroundApp:
              phoneScreenView === 'SETTINGS_DISPLAY'
                ? 'Android Settings -> Display'
                : phoneScreenView === 'SETTINGS_WIFI'
                ? 'Android Settings -> Wi-Fi'
                : phoneScreenView === 'SETTINGS_HOME'
                ? 'Android Settings Home'
                : `${currentForegroundApp.name} — ${currentForegroundApp.subtitle}`,
            visibleScreenElements: [
              'nav_settings_display',
              'nav_settings_wifi',
              'display_brightness',
              'display_dark_theme',
              'display_timeout',
              'wifi_network_5g',
              'wifi_connect_btn',
              'app_action_primary',
              'app_action_secondary',
              'app_action_export',
            ],
            intelligenceMode,
            grantedApps: allowedAppList,
            deviceContext: {
              displayBrightness,
              amoledDarkTheme,
              wifiConnectedSsid,
            },
            conversationHistory,
          }),
        });

        if (!response.ok) {
          throw new Error('Intelligence API error');
        }

        const result = await response.json();

        if (
          result.isStepByStepGuidance &&
          Array.isArray(result.guidedSteps) &&
          result.guidedSteps.length > 0
        ) {
          const firstTarget = result.guidedSteps[0].targetElementId || '';
          const targetView: PhoneScreenView = firstTarget.startsWith('display_')
            ? 'SETTINGS_DISPLAY'
            : firstTarget.startsWith('wifi_')
            ? 'SETTINGS_WIFI'
            : firstTarget.startsWith('nav_settings')
            ? 'SETTINGS_HOME'
            : 'INSTALLED_APP';

          startGuidedTask(
            result.guidedTaskTitle || 'Live Step-by-Step Guidance',
            result.guidedSteps,
            targetView
          );
          return;
        }

        const spoken =
          result.spokenResponse ||
          `Done, ${latestHonorificRef.current}. I have processed your request on the Dynamic Island.`;

        setJarvisResponse(spoken);
        setIslandCardData({
          domain: result.intelligenceDomain || intelligenceMode,
          languageLabel: `${result.detectedLanguageLabel || currentLanguageObj.name} · ${
            latestHonorificRef.current
          }`,
          actionPerformed: result.actionPerformed || 'Processed via JARVIS ULTRA',
          structuredTitle: result.structuredCardTitle || 'JARVIS ULTRA Insights',
          structuredBullets: Array.isArray(result.structuredCardBullets)
            ? result.structuredCardBullets
            : [],
        });

        setConversationHistory((prev) => [
          ...prev.slice(-8),
          { role: 'User', text: cleaned },
          { role: 'JARVIS', text: spoken },
        ]);

        speakJarvisVoice(spoken);
      } catch {
        const fallback = `Hi ${latestHonorificRef.current}, welcome to the JARVIS world, how can I help you?`;
        setJarvisResponse(fallback);
        speakJarvisVoice(fallback);
      }
    },
    [
      activeTaskTitle,
      amoledDarkTheme,
      conversationHistory,
      currentForegroundApp.name,
      currentForegroundApp.subtitle,
      currentLanguageObj.code,
      currentLanguageObj.name,
      displayBrightness,
      explainVisibleScreen,
      grantedApps,
      handleNextStep,
      handlePauseResumeGuidance,
      handlePreviousStep,
      handleRepeatStepInstruction,
      handleStopGuidance,
      intelligenceMode,
      isGuidancePaused,
      phoneScreenView,
      speakJarvisVoice,
      startGuidedTask,
      triggerHeyJarvisWakeWelcome,
      voicePersona,
      wifiConnectedSsid,
    ]
  );

  // Start Web Audio API Microphone Pitch Analyzer to automatically classify Male ("Sir") vs Female ("Ma'am") voice
  const startMicrophonePitchAnalyzer = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaStreamRef.current = stream;
      const audioCtx = new AudioContext();
      audioContextRef.current = audioCtx;
      const source = audioCtx.createMediaStreamSource(stream);
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 2048;
      source.connect(analyser);

      const buffer = new Float32Array(analyser.fftSize);

      pitchIntervalRef.current = window.setInterval(() => {
        analyser.getFloatTimeDomainData(buffer);
        // Compute RMS energy & zero-crossing fundamental pitch estimate
        let sumSquares = 0;
        let crossings = 0;
        for (let i = 1; i < buffer.length; i++) {
          sumSquares += buffer[i] * buffer[i];
          if (
            (buffer[i - 1] >= 0 && buffer[i] < 0) ||
            (buffer[i - 1] < 0 && buffer[i] >= 0)
          ) {
            crossings++;
          }
        }
        const rms = Math.sqrt(sumSquares / buffer.length);
        if (rms > 0.02) {
          const estHz = Math.round(
            (crossings * audioCtx.sampleRate) / (2 * buffer.length)
          );
          if (estHz >= 85 && estHz <= 300) {
            setDetectedUserPitchHz(estHz);
            const nextHonorific: DetectedUserHonorific =
              estHz >= 165 ? "Ma'am" : 'Sir';
            setUserHonorific(nextHonorific);
          }
        }
      }, 300);
    } catch {
      // Microphone stream unavailable in iframe; user can toggle Sir / Ma'am directly
    }
  };

  const stopMicrophonePitchAnalyzer = () => {
    if (pitchIntervalRef.current !== null) {
      window.clearInterval(pitchIntervalRef.current);
      pitchIntervalRef.current = null;
    }
    mediaStreamRef.current?.getTracks().forEach((t) => t.stop());
    mediaStreamRef.current = null;
    audioContextRef.current?.close().catch(() => {});
    audioContextRef.current = null;
  };

  // Toggle Continuous Voice Recognition + Live Voice Pitch ("Sir" / "Ma'am") Detection
  const toggleContinuousVoiceStream = () => {
    if (continuousVoiceActive) {
      speechRecognitionRef.current?.stop();
      speechRecognitionRef.current = null;
      stopMicrophonePitchAnalyzer();
      setContinuousVoiceActive(false);
      setVoiceStatusText('Continuous microphone stream paused.');
      return;
    }

    startMicrophonePitchAnalyzer();

    const SpeechRecognitionApi =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognitionApi) {
      setVoiceStatusText(
        'Browser frame does not expose native SpeechRecognition. Use the "Say Hey Jarvis" buttons or command bar below.'
      );
      return;
    }

    try {
      const recognition = new SpeechRecognitionApi();
      recognition.continuous = true;
      recognition.interimResults = false;
      recognition.lang =
        selectedLanguageCode === 'hi-Latn' ? 'hi-IN' : selectedLanguageCode;

      recognition.onstart = () => {
        setContinuousVoiceActive(true);
        setOrbState(OrbVisualState.LISTENING);
        setVoiceStatusText(
          `Always-On Voice Recognition Active — Say "Hey Jarvis" (Auto-detecting Sir / Ma'am from your voice)`
        );
      };

      recognition.onresult = (event: any) => {
        const lastIdx = event.results.length - 1;
        const transcript = event.results[lastIdx][0].transcript.trim();
        if (transcript) {
          processJarvisVoiceCommand(transcript);
        }
      };

      recognition.onerror = () => {
        setContinuousVoiceActive(false);
      };

      recognition.onend = () => {
        setContinuousVoiceActive(false);
      };

      speechRecognitionRef.current = recognition;
      recognition.start();
    } catch {
      setVoiceStatusText('Use the voice command bar or quick triggers below.');
    }
  };

  const handleDownloadAndroidBundle = () => {
    const bundleContent = ANDROID_SOURCE_FILES.map(
      (f) =>
        `================================================================================\nFILE: ${f.filePath}\n================================================================================\n\n${f.code}\n\n`
    ).join('\n');
    const blob = new Blob([bundleContent], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'jarvis-ultra-live-line-navigation-android.txt';
    a.click();
    URL.revokeObjectURL(url);
  };

  const selectedSourceFile =
    ANDROID_SOURCE_FILES.find((f) => f.id === selectedSourceId) || ANDROID_SOURCE_FILES[0];

  const onPhoneElementTapped = (navId: string, actionCallback: () => void) => {
    actionCallback();
    const currentStep = guidedSteps[currentStepIdx];
    if (activeTaskTitle && !isGuidancePaused && currentStep?.targetElementId === navId) {
      window.setTimeout(() => {
        handleNextStep();
      }, 220);
    }
  };

  return (
    <div className="min-h-screen bg-[#060913] text-slate-100 flex flex-col">
      {/* Top Bar Contract: Strictly 3 zones separated by gap-8 */}
      <header className="flex items-center justify-between gap-8 px-6 py-4 border-b border-white/10 bg-[#060913]/90 sticky top-0 z-40 backdrop-blur-xl">
        {/* Zone 1: Single text element wordmark */}
        <a
          href="#simulator"
          onClick={(e) => {
            e.preventDefault();
            setActiveSection('simulator');
          }}
          className="text-lg font-bold tracking-tight text-white whitespace-nowrap shrink-0"
        >
          JARVIS ULTRA · Dynamic Island AI
        </a>

        {/* Zone 2: 4 concise single-line text navigation links */}
        <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-400">
          <button
            type="button"
            onClick={() => setActiveSection('simulator')}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 pb-0.5 ${
              activeSection === 'simulator'
                ? 'text-cyan-300 border-b border-cyan-400'
                : 'text-slate-400'
            }`}
          >
            Live Line Navigation
          </button>
          <button
            type="button"
            onClick={() => setActiveSection('customizer')}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 pb-0.5 ${
              activeSection === 'customizer'
                ? 'text-cyan-300 border-b border-cyan-400'
                : 'text-slate-400'
            }`}
          >
            Island & Voice Customizer
          </button>
          <button
            type="button"
            onClick={() => setActiveSection('source')}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 pb-0.5 ${
              activeSection === 'source'
                ? 'text-cyan-300 border-b border-cyan-400'
                : 'text-slate-400'
            }`}
          >
            Android APK Source
          </button>
          <button
            type="button"
            onClick={() => setActiveSection('validation')}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 pb-0.5 ${
              activeSection === 'validation'
                ? 'text-cyan-300 border-b border-cyan-400'
                : 'text-slate-400'
            }`}
          >
            Validation Checklist
          </button>
        </nav>

        {/* Zone 3: 1 primary action */}
        <div className="flex items-center gap-3 shrink-0">
          <button
            type="button"
            onClick={handleDownloadAndroidBundle}
            className="px-4 py-2 text-xs font-semibold text-slate-950 bg-cyan-400 rounded-lg hover:bg-cyan-300 transition-colors whitespace-nowrap shrink-0 flex items-center gap-1.5"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Export GitHub APK Bundle</span>
          </button>
        </div>
      </header>

      {/* Main Content Container */}
      <main className="flex-1 max-w-[1420px] w-full mx-auto px-6 py-6">
        {activeSection === 'simulator' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* LEFT COLUMN (5 cols): Interactive Android Phone with Dynamic Island + Live Screen Line Navigation Overlay */}
            <div className="lg:col-span-5 flex flex-col items-center">
              <div
                ref={phoneViewportRef}
                className="w-full max-w-[398px] rounded-[44px] border-2 border-white/15 bg-[#040710] shadow-2xl overflow-hidden relative min-h-[760px] flex flex-col justify-between"
              >
                {/* Android Status Bar with Visible Microphone Indicator */}
                <div className="px-6 pt-3 pb-1.5 flex items-center justify-between text-[11px] font-mono text-slate-300 z-30">
                  <span className="tabular-nums">
                    {new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </span>
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] text-cyan-300">
                      User: {userHonorific} ({detectedUserPitchHz}Hz)
                    </span>
                    <span
                      className="px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 text-[10px] flex items-center gap-1"
                      title="Visible Microphone Indicator while listening"
                    >
                      <Mic className="w-2.5 h-2.5" />
                      <span>MIC ON</span>
                    </span>
                  </div>
                </div>

                {/* DYNAMIC ISLAND-STYLE JARVIS PILL */}
                {permissions.systemAlertWindow ? (
                  <LiquidGlassDynamicIsland
                    orbState={orbState}
                    isExpanded={isIslandExpanded}
                    setIsExpanded={setIsIslandExpanded}
                    userTranscript={userTranscript}
                    jarvisResponse={jarvisResponse}
                    waveformBars={waveformBars}
                    cardData={islandCardData}
                    idleSecondsRemaining={idleCountdown}
                    customTheme={customTheme}
                    activeTaskTitle={activeTaskTitle}
                    currentStepNumber={currentStepIdx + 1}
                    totalSteps={guidedSteps.length}
                    isGuidancePaused={isGuidancePaused}
                    onPrevStep={handlePreviousStep}
                    onNextStep={handleNextStep}
                    onPauseResumeGuidance={handlePauseResumeGuidance}
                    onRepeatStep={handleRepeatStepInstruction}
                    onStopGuidance={handleStopGuidance}
                    onGrantAppPermission={(appId) => {
                      setGrantedApps((prev) => ({ ...prev, [appId]: true }));
                    }}
                    onDenyAppPermission={() => {
                      setPendingPermissionCommand(null);
                    }}
                    onDismissIsland={() => {
                      clearIdleTimer();
                      setIsIslandExpanded(false);
                    }}
                  />
                ) : (
                  <div className="mx-3 mt-1 rounded-2xl bg-slate-900 border border-amber-400/40 p-2.5 text-[11px] text-amber-200 z-30">
                    <div className="font-semibold">
                      In-App Guidance Mode (Overlay Permission Denied)
                    </div>
                    <div className="text-slate-300 mt-0.5">{jarvisResponse}</div>
                  </div>
                )}

                {/* LIVE SCREEN LINE NAVIGATION OVERLAY */}
                {permissions.systemAlertWindow && accessibilityEnabled && (
                  <LiveScreenLineNavigationOverlay
                    containerRef={phoneViewportRef}
                    isActive={Boolean(activeTaskTitle)}
                    isPaused={isGuidancePaused}
                    taskTitle={activeTaskTitle || ''}
                    currentStepIndex={currentStepIdx}
                    steps={guidedSteps}
                    neonColorHex={customTheme.primaryHex}
                    secondaryColorHex={customTheme.secondaryHex}
                    onManualTargetSelected={() => handleNextStep()}
                  />
                )}

                {/* INTERACTIVE ANDROID SCREENS UNDERNEATH OVERLAY */}
                <div className="flex-1 p-4 flex flex-col justify-between relative z-10">
                  {phoneScreenView === 'SETTINGS_HOME' && (
                    <div className="my-auto space-y-3">
                      <div className="flex items-center justify-between px-1">
                        <div>
                          <div className="text-xs font-bold text-white">Android System Settings</div>
                          <div className="text-[11px] text-slate-400">
                            Tap any highlighted option or ask JARVIS to guide you
                          </div>
                        </div>
                      </div>

                      <div className="space-y-2.5 pt-1">
                        <button
                          type="button"
                          data-nav-id="nav_settings_display"
                          onClick={() =>
                            onPhoneElementTapped('nav_settings_display', () =>
                              setPhoneScreenView('SETTINGS_DISPLAY')
                            )
                          }
                          className="w-full p-3.5 rounded-2xl bg-slate-900/85 hover:bg-slate-800 border border-white/10 flex items-center justify-between text-left transition-colors"
                        >
                          <div className="flex items-center gap-3">
                            <Sun className="w-5 h-5 text-amber-300 shrink-0" />
                            <div>
                              <div className="text-xs font-semibold text-white">
                                Display & Brightness
                              </div>
                              <div className="text-[11px] text-slate-400">
                                Brightness ({displayBrightness}%), Dark theme, Screen timeout
                              </div>
                            </div>
                          </div>
                          <span className="text-xs text-cyan-300 font-mono">Open →</span>
                        </button>

                        <button
                          type="button"
                          data-nav-id="nav_settings_wifi"
                          onClick={() =>
                            onPhoneElementTapped('nav_settings_wifi', () =>
                              setPhoneScreenView('SETTINGS_WIFI')
                            )
                          }
                          className="w-full p-3.5 rounded-2xl bg-slate-900/85 hover:bg-slate-800 border border-white/10 flex items-center justify-between text-left transition-colors"
                        >
                          <div className="flex items-center gap-3">
                            <Wifi className="w-5 h-5 text-cyan-300 shrink-0" />
                            <div>
                              <div className="text-xs font-semibold text-white">
                                Network & Internet (Wi-Fi)
                              </div>
                              <div className="text-[11px] text-slate-400">
                                Connected to {wifiConnectedSsid}
                              </div>
                            </div>
                          </div>
                          <span className="text-xs text-cyan-300 font-mono">Open →</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => setPhoneScreenView('INSTALLED_APP')}
                          className="w-full p-3.5 rounded-2xl bg-slate-900/85 hover:bg-slate-800 border border-white/10 flex items-center justify-between text-left transition-colors"
                        >
                          <div className="flex items-center gap-3">
                            <Sparkles className="w-5 h-5 text-purple-300 shrink-0" />
                            <div>
                              <div className="text-xs font-semibold text-white">
                                Active App: {currentForegroundApp.name}
                              </div>
                              <div className="text-[11px] text-slate-400">
                                {currentForegroundApp.subtitle}
                              </div>
                            </div>
                          </div>
                          <span className="text-xs text-cyan-300 font-mono">Switch →</span>
                        </button>
                      </div>
                    </div>
                  )}

                  {phoneScreenView === 'SETTINGS_DISPLAY' && (
                    <div className="my-auto space-y-3">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-white">
                          Settings · Display Preferences
                        </span>
                        <button
                          type="button"
                          onClick={() => setPhoneScreenView('SETTINGS_HOME')}
                          className="text-[11px] text-cyan-300 hover:underline"
                        >
                          ← Back to Settings
                        </button>
                      </div>

                      <div className="space-y-2.5">
                        <button
                          type="button"
                          data-nav-id="display_brightness"
                          onClick={() =>
                            onPhoneElementTapped('display_brightness', () =>
                              setDisplayBrightness((b) => (b >= 90 ? 60 : 92))
                            )
                          }
                          className="w-full p-3.5 rounded-2xl bg-slate-900/90 hover:bg-slate-800 border border-white/10 flex items-center justify-between text-left"
                        >
                          <div className="flex items-center gap-3">
                            <Sun className="w-4 h-4 text-amber-300" />
                            <div>
                              <div className="text-xs font-semibold text-white">
                                Brightness Level
                              </div>
                              <div className="text-[11px] text-slate-400">
                                Tap to calibrate optimal HDR luminance
                              </div>
                            </div>
                          </div>
                          <span className="text-xs font-mono font-bold text-amber-300">
                            {displayBrightness}%
                          </span>
                        </button>

                        <button
                          type="button"
                          data-nav-id="display_dark_theme"
                          onClick={() =>
                            onPhoneElementTapped('display_dark_theme', () =>
                              setAmoledDarkTheme((d) => !d)
                            )
                          }
                          className="w-full p-3.5 rounded-2xl bg-slate-900/90 hover:bg-slate-800 border border-white/10 flex items-center justify-between text-left"
                        >
                          <div className="flex items-center gap-3">
                            <Moon className="w-4 h-4 text-cyan-300" />
                            <div>
                              <div className="text-xs font-semibold text-white">
                                AMOLED Dark Theme
                              </div>
                              <div className="text-[11px] text-slate-400">
                                Reduces power draw & eye strain
                              </div>
                            </div>
                          </div>
                          <span className="text-xs font-mono font-bold text-emerald-300">
                            {amoledDarkTheme ? 'ENABLED' : 'OFF'}
                          </span>
                        </button>

                        <button
                          type="button"
                          data-nav-id="display_timeout"
                          onClick={() =>
                            onPhoneElementTapped('display_timeout', () =>
                              setScreenTimeoutSec((s) => (s === 60 ? 300 : 60))
                            )
                          }
                          className="w-full p-3.5 rounded-2xl bg-slate-900/90 hover:bg-slate-800 border border-white/10 flex items-center justify-between text-left"
                        >
                          <div className="flex items-center gap-3">
                            <Clock className="w-4 h-4 text-purple-300" />
                            <div>
                              <div className="text-xs font-semibold text-white">Screen Timeout</div>
                              <div className="text-[11px] text-slate-400">
                                Inactivity sleep timer
                              </div>
                            </div>
                          </div>
                          <span className="text-xs font-mono text-slate-200">
                            {screenTimeoutSec}s
                          </span>
                        </button>
                      </div>
                    </div>
                  )}

                  {phoneScreenView === 'SETTINGS_WIFI' && (
                    <div className="my-auto space-y-3">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-white">
                          Settings · Wi-Fi Networks
                        </span>
                        <button
                          type="button"
                          onClick={() => setPhoneScreenView('SETTINGS_HOME')}
                          className="text-[11px] text-cyan-300 hover:underline"
                        >
                          ← Back to Settings
                        </button>
                      </div>

                      <div className="space-y-2.5">
                        <button
                          type="button"
                          data-nav-id="wifi_network_5g"
                          onClick={() =>
                            onPhoneElementTapped('wifi_network_5g', () =>
                              setWifiConnectedSsid('Stark_5G_Ultra (Selected)')
                            )
                          }
                          className="w-full p-3.5 rounded-2xl bg-slate-900/90 hover:bg-slate-800 border border-white/10 flex items-center justify-between text-left"
                        >
                          <div className="flex items-center gap-3">
                            <Wifi className="w-4 h-4 text-cyan-300" />
                            <div>
                              <div className="text-xs font-semibold text-white">
                                Stark_5G_Ultra (6GHz Gaming Band)
                              </div>
                              <div className="text-[11px] text-slate-400">
                                WPA3 Personal · 1.2 Gbps
                              </div>
                            </div>
                          </div>
                          <span className="text-xs font-mono text-cyan-300">Select</span>
                        </button>

                        <button
                          type="button"
                          data-nav-id="wifi_connect_btn"
                          onClick={() =>
                            onPhoneElementTapped('wifi_connect_btn', () =>
                              setWifiConnectedSsid('Stark_5G_Ultra (Connected)')
                            )
                          }
                          className="w-full py-3 px-4 rounded-2xl bg-cyan-500/20 hover:bg-cyan-500/30 border border-cyan-400/50 text-xs font-bold text-cyan-200 text-center"
                        >
                          Verify & Connect to Selected Network
                        </button>
                      </div>
                    </div>
                  )}

                  {phoneScreenView === 'INSTALLED_APP' && (
                    <div className="my-auto space-y-3">
                      <div className="flex items-center justify-between">
                        <div>
                          <div
                            className={`text-xs font-bold ${currentForegroundApp.accentColor}`}
                          >
                            {currentForegroundApp.name}
                          </div>
                          <div className="text-[10px] text-slate-400">
                            {currentForegroundApp.subtitle}
                          </div>
                        </div>
                        <button
                          type="button"
                          onClick={() => setPhoneScreenView('SETTINGS_HOME')}
                          className="text-[11px] text-cyan-300 hover:underline"
                        >
                          Settings Home
                        </button>
                      </div>

                      <div className="rounded-2xl bg-slate-950/90 border border-white/10 p-3 space-y-1">
                        {currentForegroundApp.liveDataSummary.map((line, i) => (
                          <div key={i} className="text-[11px] text-slate-400 font-mono">
                            • {line}
                          </div>
                        ))}
                      </div>

                      <div className="grid grid-cols-1 gap-2">
                        <button
                          type="button"
                          data-nav-id="app_action_primary"
                          onClick={() =>
                            onPhoneElementTapped('app_action_primary', () => {})
                          }
                          className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-white/10 text-left flex items-center justify-between text-xs"
                        >
                          <span className="font-semibold text-white">
                            1. Primary AI Action ({currentForegroundApp.name.split(' ')[0]})
                          </span>
                          <span className="text-cyan-300 font-mono text-[11px]">Tap</span>
                        </button>

                        <button
                          type="button"
                          data-nav-id="app_action_secondary"
                          onClick={() =>
                            onPhoneElementTapped('app_action_secondary', () => {})
                          }
                          className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-white/10 text-left flex items-center justify-between text-xs"
                        >
                          <span className="font-semibold text-white">
                            2. Calibrate Pro Preset Parameters
                          </span>
                          <span className="text-cyan-300 font-mono text-[11px]">Tap</span>
                        </button>

                        <button
                          type="button"
                          data-nav-id="app_action_export"
                          onClick={() =>
                            onPhoneElementTapped('app_action_export', () => {})
                          }
                          className="p-3 rounded-xl bg-cyan-500/20 hover:bg-cyan-500/30 border border-cyan-400/40 text-left flex items-center justify-between text-xs"
                        >
                          <span className="font-semibold text-cyan-200">
                            3. Apply & Finalize Task
                          </span>
                          <span className="text-cyan-200 font-mono text-[11px]">Execute</span>
                        </button>
                      </div>
                    </div>
                  )}

                  {/* Bottom Phone Screen Switcher */}
                  <div className="pt-3 border-t border-white/10 space-y-1.5">
                    <div className="grid grid-cols-4 gap-1.5">
                      <button
                        type="button"
                        onClick={() => setPhoneScreenView('SETTINGS_HOME')}
                        className={`py-1.5 px-2 rounded-xl text-[10px] font-semibold border ${
                          phoneScreenView === 'SETTINGS_HOME'
                            ? 'bg-cyan-400 text-slate-950 border-cyan-300'
                            : 'bg-slate-900 border-white/10 text-slate-300'
                        }`}
                      >
                        Settings
                      </button>
                      <button
                        type="button"
                        onClick={() => setPhoneScreenView('SETTINGS_DISPLAY')}
                        className={`py-1.5 px-2 rounded-xl text-[10px] font-semibold border ${
                          phoneScreenView === 'SETTINGS_DISPLAY'
                            ? 'bg-cyan-400 text-slate-950 border-cyan-300'
                            : 'bg-slate-900 border-white/10 text-slate-300'
                        }`}
                      >
                        Display
                      </button>
                      <button
                        type="button"
                        onClick={() => setPhoneScreenView('SETTINGS_WIFI')}
                        className={`py-1.5 px-2 rounded-xl text-[10px] font-semibold border ${
                          phoneScreenView === 'SETTINGS_WIFI'
                            ? 'bg-cyan-400 text-slate-950 border-cyan-300'
                            : 'bg-slate-900 border-white/10 text-slate-300'
                        }`}
                      >
                        Wi-Fi
                      </button>
                      <button
                        type="button"
                        onClick={() => setPhoneScreenView('INSTALLED_APP')}
                        className={`py-1.5 px-2 rounded-xl text-[10px] font-semibold border ${
                          phoneScreenView === 'INSTALLED_APP'
                            ? 'bg-cyan-400 text-slate-950 border-cyan-300'
                            : 'bg-slate-900 border-white/10 text-slate-300'
                        }`}
                      >
                        Apps
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* RIGHT COLUMN (7 cols): "HEY JARVIS" SIR/MA'AM VOICE DETECTOR + LIVE LINE NAVIGATION ENGINE + OMNI AI */}
            <div className="lg:col-span-7 space-y-6">
              {/* Panel 1: "Hey Jarvis" Automatic Sir / Ma'am Voice Welcome & Wake Engine */}
              <section className="rounded-3xl liquid-glass border border-white/15 p-6 space-y-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h1 className="text-base font-bold text-white flex items-center gap-2">
                      <UserCheck className="w-4 h-4 text-cyan-300" />
                      <span>
                        &ldquo;Hey Jarvis&rdquo; Smart Voice Detection (Hi Sir / Ma&apos;am Welcome)
                      </span>
                    </h1>
                    <p className="text-xs text-slate-300 mt-1">
                      Analyzes your voice pitch in real time when you say &ldquo;Hey Jarvis&rdquo;
                      and welcomes you with:{' '}
                      <span className="text-cyan-200 font-semibold">
                        &ldquo;Hi {userHonorific}, welcome to the JARVIS world, how can I help
                        you?&rdquo;
                      </span>
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={toggleContinuousVoiceStream}
                    className={`px-4 py-2 rounded-xl text-xs font-semibold flex items-center gap-1.5 shrink-0 ${
                      continuousVoiceActive
                        ? 'bg-emerald-400 text-slate-950'
                        : 'bg-cyan-400 text-slate-950 hover:bg-cyan-300'
                    }`}
                  >
                    <Mic className="w-3.5 h-3.5" />
                    <span>
                      {continuousVoiceActive
                        ? 'Live Mic Listening ("Hey Jarvis")'
                        : 'Start Live Voice Recognition'}
                    </span>
                  </button>
                </div>

                {/* Instant Test Triggers for Male ("Sir") vs Female ("Ma'am") Wake Word */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      setUserHonorific('Sir');
                      setDetectedUserPitchHz(124);
                      triggerHeyJarvisWakeWelcome('Sir');
                    }}
                    className={`p-3.5 rounded-2xl border text-left transition-colors ${
                      userHonorific === 'Sir'
                        ? 'bg-cyan-500/15 border-cyan-400 text-white'
                        : 'bg-slate-950/80 border-white/10 text-slate-300 hover:border-white/25'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-cyan-300">
                        Say &ldquo;Hey Jarvis&rdquo; (Male Voice · Sir)
                      </span>
                      <span className="text-[10px] font-mono text-slate-400">~124 Hz</span>
                    </div>
                    <p className="text-[11px] text-slate-300 mt-1">
                      &ldquo;Hi Sir, welcome to the JARVIS world, how can I help you?&rdquo;
                    </p>
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      setUserHonorific("Ma'am");
                      setDetectedUserPitchHz(210);
                      triggerHeyJarvisWakeWelcome("Ma'am");
                    }}
                    className={`p-3.5 rounded-2xl border text-left transition-colors ${
                      userHonorific === "Ma'am"
                        ? 'bg-purple-500/15 border-purple-400 text-white'
                        : 'bg-slate-950/80 border-white/10 text-slate-300 hover:border-white/25'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-purple-300">
                        Say &ldquo;Hey Jarvis&rdquo; (Female Voice · Ma&apos;am)
                      </span>
                      <span className="text-[10px] font-mono text-slate-400">~210 Hz</span>
                    </div>
                    <p className="text-[11px] text-slate-300 mt-1">
                      &ldquo;Hi Ma&apos;am, welcome to the JARVIS world, how can I help you?&rdquo;
                    </p>
                  </button>
                </div>

                <div className="text-[11px] font-mono text-slate-400">{voiceStatusText}</div>
              </section>

              {/* Panel 2: Live Screen Line Navigation & Step-by-Step Voice Command Console */}
              <section className="rounded-3xl liquid-glass border border-white/15 p-6 space-y-4">
                <div>
                  <h2 className="text-base font-bold text-white">
                    Live Screen Line Navigation & Omni Voice Assistant
                  </h2>
                  <p className="text-xs text-slate-300 mt-0.5">
                    Ask JARVIS to guide you step-by-step with live neon lines & glowing dots, or ask
                    any daily life, coding, science, gaming, or creator question.
                  </p>
                </div>

                {/* Quick Voice Persona & Language Switcher Row */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="text-[11px] font-medium text-slate-300 block mb-1">
                      JARVIS Voice Style (Male, Female, Boy, Girl, Soft, Funny, Chindi/Tapori):
                    </label>
                    <select
                      value={voicePersona}
                      onChange={(e) => setVoicePersona(e.target.value as VoicePersonaId)}
                      className="w-full bg-slate-950 border border-white/15 rounded-xl px-3 py-2 text-xs text-white"
                    >
                      {VOICE_PERSONAS.map((vp) => (
                        <option key={vp.id} value={vp.id}>
                          {vp.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="text-[11px] font-medium text-slate-300 block mb-1">
                      Spoken Language (All 24 Indian Languages):
                    </label>
                    <select
                      value={selectedLanguageCode}
                      onChange={(e) => setSelectedLanguageCode(e.target.value)}
                      className="w-full bg-slate-950 border border-white/15 rounded-xl px-3 py-2 text-xs text-white"
                    >
                      {INDIAN_LANGUAGES.map((lang) => (
                        <option key={lang.code} value={lang.code}>
                          {lang.name} — {lang.nativeName}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {/* Ask Any XYZ Step-by-Step Task or Question */}
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    if (!spokenInputBox.trim()) return;
                    processJarvisVoiceCommand(spokenInputBox);
                    setSpokenInputBox('');
                  }}
                  className="flex items-center gap-2"
                >
                  <input
                    type="text"
                    value={spokenInputBox}
                    onChange={(e) => setSpokenInputBox(e.target.value)}
                    placeholder='Say "Hey Jarvis" or "JARVIS, guide me to change display settings" or any XYZ task...'
                    className="flex-1 bg-slate-950 border border-white/15 rounded-xl px-3.5 py-2.5 text-xs text-white placeholder:text-slate-500 focus:outline-none focus:border-cyan-400"
                  />
                  <button
                    type="submit"
                    className="px-4 py-2.5 rounded-xl bg-cyan-400 text-slate-950 font-semibold text-xs hover:bg-cyan-300 whitespace-nowrap shrink-0"
                  >
                    Speak / Guide Me
                  </button>
                </form>

                {/* One-Tap Live Line Navigation & Voice Control Triggers */}
                <div className="space-y-2">
                  <div className="text-xs font-semibold text-cyan-300">
                    Live Screen Line Navigation Demonstrations (Click to draw live neon path on phone):
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <button
                      type="button"
                      onClick={() =>
                        startGuidedTask(
                          BUILT_IN_GUIDED_TASKS.display_settings.title,
                          BUILT_IN_GUIDED_TASKS.display_settings.steps,
                          BUILT_IN_GUIDED_TASKS.display_settings.initialView
                        )
                      }
                      className="p-3 rounded-2xl bg-slate-950/90 hover:bg-slate-900 border border-cyan-400/40 text-left space-y-0.5"
                    >
                      <div className="text-xs font-bold text-cyan-300">
                        &ldquo;JARVIS, guide me to change display settings&rdquo;
                      </div>
                      <div className="text-[11px] text-slate-400">
                        Opens Settings → Highlights Display (Step 1) → Brightness (Step 2) → Dark Theme (Step 3)
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() =>
                        startGuidedTask(
                          BUILT_IN_GUIDED_TASKS.wifi_settings.title,
                          BUILT_IN_GUIDED_TASKS.wifi_settings.steps,
                          BUILT_IN_GUIDED_TASKS.wifi_settings.initialView
                        )
                      }
                      className="p-3 rounded-2xl bg-slate-950/90 hover:bg-slate-900 border border-purple-400/40 text-left space-y-0.5"
                    >
                      <div className="text-xs font-bold text-purple-300">
                        &ldquo;JARVIS, guide me to Wi-Fi network settings&rdquo;
                      </div>
                      <div className="text-[11px] text-slate-400">
                        Draws animated neon path to Network & Internet → Select 5G → Connect
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() =>
                        startGuidedTask(
                          BUILT_IN_GUIDED_TASKS.app_workflow.title,
                          BUILT_IN_GUIDED_TASKS.app_workflow.steps,
                          BUILT_IN_GUIDED_TASKS.app_workflow.initialView
                        )
                      }
                      className="p-3 rounded-2xl bg-slate-950/90 hover:bg-slate-900 border border-emerald-400/40 text-left space-y-0.5"
                    >
                      <div className="text-xs font-bold text-emerald-300">
                        &ldquo;JARVIS, guide me step by step inside my app&rdquo;
                      </div>
                      <div className="text-[11px] text-slate-400">
                        Highlights Step 1, Step 2, Step 3 controls inside BGMI / Editor / Instagram
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={explainVisibleScreen}
                      className="p-3 rounded-2xl bg-slate-950/90 hover:bg-slate-900 border border-amber-400/40 text-left space-y-0.5"
                    >
                      <div className="text-xs font-bold text-amber-300">
                        &ldquo;JARVIS, explain what is on my screen&rdquo;
                      </div>
                      <div className="text-[11px] text-slate-400">
                        Uses Accessibility node tree to read out visible controls & status
                      </div>
                    </button>
                  </div>
                </div>
              </section>
            </div>
          </div>
        )}

        {/* SECTION 2: DYNAMIC ISLAND & VOICE PERSONA CUSTOMIZER */}
        {activeSection === 'customizer' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Dynamic Island Visual Customizer */}
            <section className="rounded-3xl liquid-glass border border-white/15 p-6 space-y-5">
              <div>
                <h1 className="text-lg font-bold text-white flex items-center gap-2">
                  <Palette className="w-5 h-5 text-cyan-300" />
                  <span>Dynamic Island & Live Line Neon Customizer</span>
                </h1>
                <p className="text-xs text-slate-400 mt-1">
                  Customize the neon edge lighting, line navigation color, pill geometry, and glass
                  blur intensity.
                </p>
              </div>

              <div className="space-y-2.5">
                <div className="text-xs font-semibold text-slate-300">Select Neon Theme:</div>
                {DYNAMIC_ISLAND_THEMES.map((theme) => (
                  <button
                    key={theme.id}
                    type="button"
                    onClick={() => setCustomTheme(theme)}
                    className={`w-full p-3.5 rounded-2xl border text-left flex items-center justify-between transition-colors ${
                      customTheme.id === theme.id
                        ? 'bg-slate-900 border-white/40'
                        : 'bg-slate-950/70 border-white/10 hover:border-white/25'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <span
                        className="w-5 h-5 rounded-full border border-white/20 shrink-0"
                        style={{
                          background: `linear-gradient(135deg, ${theme.primaryHex}, ${theme.secondaryHex})`,
                        }}
                      />
                      <span className="text-xs font-semibold text-white">{theme.name}</span>
                    </div>
                    <span className="text-[11px] font-mono text-slate-400">
                      {theme.primaryHex}
                    </span>
                  </button>
                ))}
              </div>

              <div className="space-y-2">
                <div className="text-xs font-semibold text-slate-300">
                  Dynamic Island Pill Width:
                </div>
                <div className="grid grid-cols-3 gap-2">
                  {(['compact', 'standard', 'wide'] as const).map((scale) => (
                    <button
                      key={scale}
                      type="button"
                      onClick={() => setCustomTheme((prev) => ({ ...prev, pillScale: scale }))}
                      className={`py-2 px-3 rounded-xl text-xs font-semibold capitalize border ${
                        customTheme.pillScale === scale
                          ? 'bg-cyan-400 text-slate-950 border-cyan-300'
                          : 'bg-slate-950 border-white/10 text-slate-300'
                      }`}
                    >
                      {scale}
                    </button>
                  ))}
                </div>
              </div>
            </section>

            {/* Voice Persona & Dialect Customizer */}
            <section className="rounded-3xl liquid-glass border border-white/15 p-6 space-y-5">
              <div>
                <h2 className="text-lg font-bold text-white flex items-center gap-2">
                  <Volume2 className="w-5 h-5 text-cyan-300" />
                  <span>Voice Persona & Dialect Studio</span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Switch between Deep Male, Female, Boy, Girl, Soft, Funny, or Bambaiya Chindi/Tapori
                  street slang anytime.
                </p>
              </div>

              <div className="space-y-2">
                {VOICE_PERSONAS.map((vp) => (
                  <button
                    key={vp.id}
                    type="button"
                    onClick={() => {
                      setVoicePersona(vp.id);
                      setActiveSection('simulator');
                      processJarvisVoiceCommand(
                        vp.id === 'chindi_tapori'
                          ? 'Aye Jarvis bhidu, apun ko display setting change karne ka rasta dikha!'
                          : `Hey Jarvis`
                      );
                    }}
                    className={`w-full p-3 rounded-2xl border text-left flex items-center justify-between transition-colors ${
                      voicePersona === vp.id
                        ? 'bg-cyan-500/15 border-cyan-400 text-white'
                        : 'bg-slate-950/70 border-white/10 text-slate-300 hover:border-white/25'
                    }`}
                  >
                    <div>
                      <div className="text-xs font-bold text-white">{vp.label}</div>
                      <div className="text-[11px] text-slate-400">{vp.subtitle}</div>
                    </div>
                    <span className="text-[10px] font-mono text-cyan-300 shrink-0">
                      Test Voice →
                    </span>
                  </button>
                ))}
              </div>
            </section>
          </div>
        )}

        {/* SECTION 3: ANDROID NATIVE SOURCE CODE */}
        {activeSection === 'source' && (
          <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h1 className="text-xl font-bold text-white">
                  Android Native Source Code (Live Line Navigation + GitHub APK Builder)
                </h1>
                <p className="text-sm text-slate-400 mt-1">
                  Complete Kotlin implementation in{' '}
                  <code className="text-cyan-300 font-mono">/android-jarvis/</code> including{' '}
                  <code className="text-cyan-300 font-mono">LiveLineNavigationOverlayView.kt</code>,{' '}
                  <code className="text-cyan-300 font-mono">TaskGuidanceEngine.kt</code>, and{' '}
                  <code className="text-cyan-300 font-mono">.github/workflows/build-android-apk.yml</code>.
                </p>
              </div>
              <button
                type="button"
                onClick={handleDownloadAndroidBundle}
                className="px-4 py-2 rounded-lg bg-cyan-400 text-slate-950 font-semibold text-xs hover:bg-cyan-300 flex items-center gap-1.5"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Download All Android & GitHub Files</span>
              </button>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
              <div className="lg:col-span-4 space-y-2">
                {ANDROID_SOURCE_FILES.map((file) => (
                  <button
                    key={file.id}
                    type="button"
                    onClick={() => setSelectedSourceId(file.id)}
                    className={`w-full text-left p-3.5 rounded-xl border transition-colors ${
                      selectedSourceId === file.id
                        ? 'bg-slate-900 border-cyan-500/60 text-slate-100'
                        : 'bg-slate-900/30 border-slate-800/80 text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-semibold font-mono text-cyan-300">
                        {file.title}
                      </span>
                      <span className="text-[11px] font-mono text-slate-500 uppercase">
                        {file.language}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">
                      {file.responsibility}
                    </p>
                  </button>
                ))}
              </div>

              <div className="lg:col-span-8 rounded-2xl border border-slate-800 bg-[#050811] overflow-hidden">
                <div className="px-4 py-3 border-b border-slate-800 flex items-center justify-between gap-4 bg-slate-950">
                  <span className="text-xs font-mono text-slate-300 truncate">
                    {selectedSourceFile.filePath}
                  </span>
                  <button
                    type="button"
                    onClick={() => {
                      navigator.clipboard.writeText(selectedSourceFile.code);
                      setCopiedSourceId(selectedSourceFile.id);
                      setTimeout(() => setCopiedSourceId(null), 2000);
                    }}
                    className="px-3 py-1 rounded-md bg-slate-800 hover:bg-slate-700 text-xs text-slate-200 flex items-center gap-1.5 shrink-0"
                  >
                    {copiedSourceId === selectedSourceFile.id ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                        <span>Copied</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" />
                        <span>Copy File</span>
                      </>
                    )}
                  </button>
                </div>
                <pre className="p-5 text-xs font-mono text-slate-200 overflow-x-auto leading-relaxed max-h-[620px]">
                  <code>{selectedSourceFile.code}</code>
                </pre>
              </div>
            </div>
          </div>
        )}

        {/* SECTION 4: VALIDATION CHECKLIST */}
        {activeSection === 'validation' && (
          <div className="space-y-6">
            <div>
              <h1 className="text-xl font-bold text-white">
                Live Line Navigation & Android OS Validation Checklist
              </h1>
              <p className="text-sm text-slate-400 mt-1">
                Test each scenario in the interactive simulator or mark it after testing the APK on
                your Android device.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {checklist.map((item) => (
                <div
                  key={item.id}
                  className="rounded-2xl border border-slate-800 bg-slate-900/40 p-5 flex flex-col justify-between space-y-4"
                >
                  <div className="space-y-2">
                    <div className="flex items-start justify-between gap-3">
                      <h2 className="text-sm font-semibold text-slate-100">{item.title}</h2>
                      <span
                        className={`text-[11px] font-mono font-semibold shrink-0 ${
                          item.verificationState === 'TESTED_ON_EMULATOR_OR_DEVICE'
                            ? 'text-emerald-400'
                            : item.verificationState === 'SIMULATED_IN_SANDBOX'
                            ? 'text-cyan-300'
                            : 'text-amber-300'
                        }`}
                      >
                        {item.verificationState === 'UNVERIFIED_ON_DEVICE' &&
                          'PENDING DEVICE TEST'}
                        {item.verificationState === 'SIMULATED_IN_SANDBOX' &&
                          'SIMULATED IN SANDBOX'}
                        {item.verificationState === 'TESTED_ON_EMULATOR_OR_DEVICE' &&
                          'TESTED ON DEVICE'}
                      </span>
                    </div>

                    <div className="text-xs text-slate-400">
                      <span className="font-semibold text-slate-300">Steps: </span>
                      {item.adbOrManualSteps}
                    </div>
                    <div className="text-xs text-slate-400">
                      <span className="font-semibold text-slate-300">Expected Behavior: </span>
                      {item.expectedOsBehavior}
                    </div>
                  </div>

                  <div className="pt-3 border-t border-slate-800/80 flex items-center justify-between gap-2">
                    <button
                      type="button"
                      onClick={() => {
                        setChecklist((prev) =>
                          prev.map((c) =>
                            c.id === item.id
                              ? { ...c, verificationState: 'SIMULATED_IN_SANDBOX' }
                              : c
                          )
                        );
                        setActiveSection('simulator');
                        if (item.id === 'honest-unidentified-target-fallback') {
                          const t = BUILT_IN_GUIDED_TASKS.unidentified_fallback_demo;
                          startGuidedTask(t.title, t.steps, t.initialView);
                        } else {
                          const t = BUILT_IN_GUIDED_TASKS.display_settings;
                          startGuidedTask(t.title, t.steps, t.initialView);
                        }
                      }}
                      className="px-3 py-1.5 rounded-lg bg-cyan-500/20 border border-cyan-500/40 text-cyan-300 hover:bg-cyan-500/30 text-xs font-medium"
                    >
                      {item.simulatorActionLabel}
                    </button>
                    <button
                      type="button"
                      onClick={() =>
                        setChecklist((prev) =>
                          prev.map((c) =>
                            c.id === item.id
                              ? { ...c, verificationState: 'TESTED_ON_EMULATOR_OR_DEVICE' }
                              : c
                          )
                        )
                      }
                      className="px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-emerald-950/60 text-[11px] text-slate-300 hover:text-emerald-300"
                    >
                      Mark Device-Tested
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
