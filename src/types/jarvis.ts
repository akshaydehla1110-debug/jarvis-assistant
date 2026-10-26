export enum OrbVisualState {
  HIDDEN = 'HIDDEN',
  LISTENING = 'LISTENING',
  THINKING = 'THINKING',
  SPEAKING = 'SPEAKING',
  CONFIRMATION = 'CONFIRMATION',
  COMPLETED = 'COMPLETED',
  ERROR = 'ERROR',
}

export enum ServiceLifecycleState {
  STOPPED = 'STOPPED',
  STANDBY_LISTENING = 'STANDBY_LISTENING',
  CAPTURING_COMMAND = 'CAPTURING_COMMAND',
  PAUSED_BY_USER = 'PAUSED_BY_USER',
  PAUSED_MIC_CONFLICT = 'PAUSED_MIC_CONFLICT',
  BLOCKED_PERMISSION_REVOKED = 'BLOCKED_PERMISSION_REVOKED',
  BLOCKED_BG_RESTRICTION = 'BLOCKED_BG_RESTRICTION',
}

export enum DeviceScreenContext {
  HOME_SCREEN = 'HOME_SCREEN',
  OTHER_APP_FOREGROUND = 'OTHER_APP_FOREGROUND',
  JARVIS_SETUP_ACTIVITY = 'JARVIS_SETUP_ACTIVITY',
  LOCK_SCREEN_SECURE = 'LOCK_SCREEN_SECURE',
}

export interface PermissionMatrix {
  recordAudio: boolean;
  postNotifications: boolean;
  systemAlertWindow: boolean;
  readContacts: boolean;
  batteryOptimizationIgnored: boolean;
}

export interface LocalReminder {
  id: string;
  text: string;
  createdAt: string;
}

export interface ContactEntry {
  id: string;
  name: string;
  phone: string;
  role: string;
}

export interface PendingConfirmation {
  id: string;
  commandCategory: string;
  actionSummary: string;
  previewPayload: string;
  spokenPrompt: string;
  intentSignature: string;
  onConfirm: () => { spokenResponse: string; detailLog: string };
}

export interface CommandEvaluationResult {
  status: 'EXECUTED' | 'REQUIRES_CONFIRMATION' | 'BLOCKED_LOCK_SCREEN' | 'REJECTED_ALLOWLIST' | 'CANCELLED';
  category: string;
  intentSignature: string;
  spokenResponse: string;
  detailLog: string;
  pendingConfirmation?: PendingConfirmation;
}

export interface AuditLogEntry {
  id: string;
  timestamp: string;
  triggerSource: 'Wake Phrase ("JARVIS")' | 'Push-to-Talk' | 'Voice Confirmation' | 'System Event';
  utterance: string;
  outcomeStatus: CommandEvaluationResult['status'] | 'SYSTEM_NOTICE';
  intentSignature: string;
  spokenResponse: string;
}

export type ChecklistVerificationState =
  | 'UNVERIFIED_ON_DEVICE'
  | 'SIMULATED_IN_SANDBOX'
  | 'TESTED_ON_EMULATOR_OR_DEVICE'
  | 'FAILED_NEEDS_ATTENTION';

export interface ValidationScenario {
  id: string;
  title: string;
  category: string;
  adbOrManualSteps: string;
  expectedOsBehavior: string;
  osLimitationNote: string;
  verificationState: ChecklistVerificationState;
  simulatorActionLabel: string;
}
