import {
  CommandEvaluationResult,
  ContactEntry,
  DeviceScreenContext,
  LocalReminder,
  PermissionMatrix,
} from '../types/jarvis';

export interface DeviceHardwareState {
  flashlightOn: boolean;
  mediaVolume: number; // 0 to 100
  activeAppTitle: string;
  accessibilityAutomationActive: boolean;
  reminders: LocalReminder[];
  contacts: ContactEntry[];
  screenContext: DeviceScreenContext;
  permissions: PermissionMatrix;
}

export interface StateMutators {
  setFlashlightOn: (on: boolean) => void;
  setMediaVolume: (vol: number) => void;
  setActiveAppTitle: (app: string) => void;
  setAccessibilityAutomationActive: (active: boolean) => void;
  addReminder: (text: string) => void;
  setScreenContext: (ctx: DeviceScreenContext) => void;
}

const ALLOWLISTED_APPS: Record<string, { label: string; packageName: string }> = {
  calculator: { label: 'Calculator', packageName: 'com.google.android.calculator' },
  calendar: { label: 'Google Calendar', packageName: 'com.google.android.calendar' },
  clock: { label: 'Clock & Alarms', packageName: 'com.google.android.deskclock' },
  maps: { label: 'Google Maps', packageName: 'com.google.android.apps.maps' },
  spotify: { label: 'Spotify Music', packageName: 'com.spotify.music' },
  camera: { label: 'Camera', packageName: 'com.android.camera' },
};

/**
 * Deterministic, strict-allowlist command router.
 * Never executes arbitrary AI-generated code or shell commands.
 */
export function evaluateVoiceCommand(
  rawUtterance: string,
  state: DeviceHardwareState,
  mutators: StateMutators
): CommandEvaluationResult {
  const cleaned = rawUtterance.trim();
  const normalized = cleaned
    .toLowerCase()
    .replace(/^hey\s+jarvis[,.\s]*/i, '')
    .replace(/^jarvis[,.\s]*/i, '')
    .trim();

  if (!normalized) {
    return {
      status: 'REJECTED_ALLOWLIST',
      category: 'Wake Phrase Only',
      intentSignature: 'NONE',
      spokenResponse: 'Listening. Please state an allowlisted command.',
      detailLog: 'Wake word detected without trailing command.',
    };
  }

  // Voice cancellation at any point
  if (['cancel', 'stop', 'never mind', 'dismiss', 'abort'].includes(normalized)) {
    return {
      status: 'CANCELLED',
      category: 'Voice Cancellation',
      intentSignature: 'ACTION_CANCEL_CURRENT',
      spokenResponse: 'Cancelled.',
      detailLog: 'User cancelled active voice command flow.',
    };
  }

  // 1. Hardware: Flashlight (Permitted on lock screen)
  if (
    normalized.includes('flashlight on') ||
    normalized.includes('turn on flashlight') ||
    normalized.includes('turn on the flashlight') ||
    normalized === 'torch on'
  ) {
    mutators.setFlashlightOn(true);
    return {
      status: 'EXECUTED',
      category: 'Hardware · Flashlight',
      intentSignature: 'CameraManager.setTorchMode(cameraId, true)',
      spokenResponse: 'Flashlight turned on.',
      detailLog: 'Toggled torch mode ON via CameraManager.',
    };
  }

  if (
    normalized.includes('flashlight off') ||
    normalized.includes('turn off flashlight') ||
    normalized.includes('turn off the flashlight') ||
    normalized === 'torch off'
  ) {
    mutators.setFlashlightOn(false);
    return {
      status: 'EXECUTED',
      category: 'Hardware · Flashlight',
      intentSignature: 'CameraManager.setTorchMode(cameraId, false)',
      spokenResponse: 'Flashlight turned off.',
      detailLog: 'Toggled torch mode OFF via CameraManager.',
    };
  }

  // 2. Hardware: Volume controls (Permitted on lock screen)
  if (normalized.includes('volume up') || normalized.includes('increase volume') || normalized.includes('louder')) {
    const nextVol = Math.min(100, state.mediaVolume + 20);
    mutators.setMediaVolume(nextVol);
    return {
      status: 'EXECUTED',
      category: 'Hardware · Audio',
      intentSignature: 'AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE)',
      spokenResponse: `Media volume increased to ${nextVol} percent.`,
      detailLog: `Adjusted STREAM_MUSIC volume from ${state.mediaVolume}% to ${nextVol}%.`,
    };
  }

  if (normalized.includes('volume down') || normalized.includes('decrease volume') || normalized.includes('quieter')) {
    const nextVol = Math.max(0, state.mediaVolume - 20);
    mutators.setMediaVolume(nextVol);
    return {
      status: 'EXECUTED',
      category: 'Hardware · Audio',
      intentSignature: 'AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER)',
      spokenResponse: `Media volume lowered to ${nextVol} percent.`,
      detailLog: `Adjusted STREAM_MUSIC volume from ${state.mediaVolume}% to ${nextVol}%.`,
    };
  }

  if (normalized.includes('mute volume') || normalized.includes('mute audio')) {
    mutators.setMediaVolume(0);
    return {
      status: 'EXECUTED',
      category: 'Hardware · Audio',
      intentSignature: 'AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_MUTE)',
      spokenResponse: 'Media audio muted.',
      detailLog: 'Muted STREAM_MUSIC channel.',
    };
  }

  // 3. Local Questions (Time / Date / Status)
  if (normalized.includes('what time') || normalized.includes('current time') || normalized === 'time') {
    const nowStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    return {
      status: 'EXECUTED',
      category: 'Local Query · Clock',
      intentSignature: 'LocalClock.queryCurrentTime()',
      spokenResponse: `It is currently ${nowStr}.`,
      detailLog: `Answered local time query (${nowStr}) with zero network calls.`,
    };
  }

  // 4. Lock Screen Security Gate (KeyguardManager.isKeyguardLocked)
  if (state.screenContext === DeviceScreenContext.LOCK_SCREEN_SECURE) {
    return {
      status: 'BLOCKED_LOCK_SCREEN',
      category: 'Keyguard Security Gate',
      intentSignature: 'KeyguardManager.isKeyguardLocked == true',
      spokenResponse: 'Device is locked. Please unlock your phone with biometric or PIN for this command.',
      detailLog:
        'Blocked protected operation over secure lock screen. Sensitive content and intents require device unlock.',
    };
  }

  // 5. Android Settings Pages
  if (normalized.includes('wifi settings') || normalized.includes('wi-fi settings') || normalized === 'open wifi') {
    mutators.setActiveAppTitle('Android Settings · Wi-Fi');
    mutators.setScreenContext(DeviceScreenContext.OTHER_APP_FOREGROUND);
    return {
      status: 'EXECUTED',
      category: 'Android Settings',
      intentSignature: 'Intent(Settings.ACTION_WIFI_SETTINGS)',
      spokenResponse: 'Opening Wi-Fi settings.',
      detailLog: 'Dispatched Settings.ACTION_WIFI_SETTINGS with FLAG_ACTIVITY_NEW_TASK.',
    };
  }

  if (normalized.includes('bluetooth settings') || normalized === 'open bluetooth') {
    mutators.setActiveAppTitle('Android Settings · Bluetooth');
    mutators.setScreenContext(DeviceScreenContext.OTHER_APP_FOREGROUND);
    return {
      status: 'EXECUTED',
      category: 'Android Settings',
      intentSignature: 'Intent(Settings.ACTION_BLUETOOTH_SETTINGS)',
      spokenResponse: 'Opening Bluetooth settings.',
      detailLog: 'Dispatched Settings.ACTION_BLUETOOTH_SETTINGS with FLAG_ACTIVITY_NEW_TASK.',
    };
  }

  if (normalized.includes('battery settings') || normalized.includes('battery optimization')) {
    mutators.setActiveAppTitle('Android Settings · Battery Optimization');
    mutators.setScreenContext(DeviceScreenContext.OTHER_APP_FOREGROUND);
    return {
      status: 'EXECUTED',
      category: 'Android Settings',
      intentSignature: 'Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)',
      spokenResponse: 'Opening battery optimization settings.',
      detailLog: 'Dispatched Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS.',
    };
  }

  // 6. Opening Allowlisted Applications
  if (normalized.startsWith('open ') || normalized.startsWith('launch ')) {
    const appTarget = normalized.replace(/^(open|launch)\s+/i, '').trim();
    const matchedKey = Object.keys(ALLOWLISTED_APPS).find((key) => appTarget.includes(key));
    if (matchedKey) {
      const appInfo = ALLOWLISTED_APPS[matchedKey];
      mutators.setActiveAppTitle(appInfo.label);
      mutators.setScreenContext(DeviceScreenContext.OTHER_APP_FOREGROUND);
      return {
        status: 'EXECUTED',
        category: 'App Launch · Allowlist',
        intentSignature: `PackageManager.getLaunchIntentForPackage("${appInfo.packageName}")`,
        spokenResponse: `Opening ${appInfo.label}.`,
        detailLog: `Launched allowlisted package ${appInfo.packageName} while keeping floating service active.`,
      };
    }
    return {
      status: 'REJECTED_ALLOWLIST',
      category: 'App Launch · Rejected',
      intentSignature: `BLOCKED_PACKAGE("${appTarget}")`,
      spokenResponse: `Sorry, ${appTarget} is not in the allowlisted application registry.`,
      detailLog: `Rejected non-allowlisted package launch request: "${appTarget}".`,
    };
  }

  // 7. Creating Local Reminders & Notes
  if (
    normalized.startsWith('remind me to ') ||
    normalized.startsWith('create note ') ||
    normalized.startsWith('add reminder ')
  ) {
    const noteBody = cleaned
      .replace(/^hey\s+jarvis[,.\s]*/i, '')
      .replace(/^jarvis[,.\s]*/i, '')
      .replace(/^(remind me to|create note|add reminder)\s+/i, '')
      .trim();

    if (noteBody.length > 0) {
      mutators.addReminder(noteBody);
      return {
        status: 'EXECUTED',
        category: 'Local Storage · Note/Reminder',
        intentSignature: 'LocalReminderStore.insert(noteBody)',
        spokenResponse: `Saved local reminder: ${noteBody}.`,
        detailLog: `Stored encrypted local reminder: "${noteBody}".`,
      };
    }
  }

  // 8. Reading Local Reminders
  if (
    normalized.includes('read my reminders') ||
    normalized.includes('read reminders') ||
    normalized.includes('what are my reminders') ||
    normalized.includes('read notes')
  ) {
    if (state.reminders.length === 0) {
      return {
        status: 'EXECUTED',
        category: 'Local Storage · Query',
        intentSignature: 'LocalReminderStore.queryAll()',
        spokenResponse: 'You have no local reminders saved.',
        detailLog: 'Read 0 reminders from local store.',
      };
    }
    const topItems = state.reminders
      .slice(0, 3)
      .map((r, idx) => `${idx + 1}: ${r.text}`)
      .join('. ');
    return {
      status: 'EXECUTED',
      category: 'Local Storage · Query',
      intentSignature: 'LocalReminderStore.queryAll()',
      spokenResponse: `You have ${state.reminders.length} reminders. ${topItems}.`,
      detailLog: `Read ${state.reminders.length} local reminders via Android TextToSpeech.`,
    };
  }

  // 9. Finding Contacts (Requires READ_CONTACTS permission)
  if (normalized.startsWith('find contact ') || normalized.startsWith('lookup ') || normalized.startsWith('who is ')) {
    if (!state.permissions.readContacts) {
      return {
        status: 'REJECTED_ALLOWLIST',
        category: 'Contacts · Permission Missing',
        intentSignature: 'Manifest.permission.READ_CONTACTS == DENIED',
        spokenResponse: 'Contacts permission is not granted. Enable Read Contacts in permissions first.',
        detailLog: 'Blocked ContactsContract query because READ_CONTACTS permission is disabled.',
      };
    }
    const query = normalized.replace(/^(find contact|lookup|who is)\s+/i, '').trim();
    const match = state.contacts.find((c) => c.name.toLowerCase().includes(query));
    if (match) {
      return {
        status: 'EXECUTED',
        category: 'Contacts · Lookup',
        intentSignature: `ContactsContract.Data.query("${match.name}")`,
        spokenResponse: `Found ${match.name}, ${match.role}, phone ending in ${match.phone.slice(-4)}.`,
        detailLog: `Matched local contact ${match.name} (${match.phone}).`,
      };
    }
    return {
      status: 'EXECUTED',
      category: 'Contacts · Lookup',
      intentSignature: `ContactsContract.Data.query("${query}")`,
      spokenResponse: `No contact matching ${query} was found.`,
      detailLog: `Zero matches in ContactsContract for "${query}".`,
    };
  }

  // 10. Consequential Action: Prepare Phone Call through Dialer (Requires Confirmation)
  if (normalized.startsWith('call ') || normalized.startsWith('dial ')) {
    const targetName = cleaned
      .replace(/^hey\s+jarvis[,.\s]*/i, '')
      .replace(/^jarvis[,.\s]*/i, '')
      .replace(/^(call|dial)\s+/i, '')
      .trim();
    const matchedContact = state.contacts.find((c) =>
      c.name.toLowerCase().includes(targetName.toLowerCase())
    );
    const displayTarget = matchedContact ? `${matchedContact.name} (${matchedContact.phone})` : targetName;
    const dialUri = matchedContact ? `tel:${matchedContact.phone}` : 'tel:555-0199';

    return {
      status: 'REQUIRES_CONFIRMATION',
      category: 'Consequential · Phone Dialer',
      intentSignature: `Intent(Intent.ACTION_DIAL, Uri.parse("${dialUri}"))`,
      spokenResponse: `Confirm preparing dialer call to ${matchedContact ? matchedContact.name : targetName}? Say Confirm or Cancel.`,
      detailLog: `Awaiting explicit user confirmation before launching ACTION_DIAL for ${displayTarget}.`,
      pendingConfirmation: {
        id: `conf_call_${Date.now()}`,
        commandCategory: 'Phone Dialer Preparation',
        actionSummary: `Prepare Dialer: ${displayTarget}`,
        previewPayload: `Intent.ACTION_DIAL -> ${dialUri} (Does not auto-place call without user tap in Dialer)`,
        spokenPrompt: `Prepare a call to ${matchedContact ? matchedContact.name : targetName} in the dialer? Say Confirm or Cancel.`,
        intentSignature: `Intent(Intent.ACTION_DIAL, Uri.parse("${dialUri}"))`,
        onConfirm: () => {
          mutators.setActiveAppTitle(`Phone Dialer · ${displayTarget}`);
          mutators.setScreenContext(DeviceScreenContext.OTHER_APP_FOREGROUND);
          return {
            spokenResponse: `Opened phone dialer for ${matchedContact ? matchedContact.name : targetName}.`,
            detailLog: `Confirmed and launched Intent.ACTION_DIAL (${dialUri}).`,
          };
        },
      },
    };
  }

  // 11. Consequential Action: Prepare SMS Message with Preview & Explicit Confirmation
  if (normalized.startsWith('text ') || normalized.startsWith('send message to ') || normalized.startsWith('message ')) {
    const remainder = cleaned
      .replace(/^hey\s+jarvis[,.\s]*/i, '')
      .replace(/^jarvis[,.\s]*/i, '')
      .replace(/^(send message to|message|text)\s+/i, '')
      .trim();

    // Parse recipient and message body
    const parts = remainder.split(/\s+saying\s+|\s+that\s+|:\s*/i);
    const recipientRaw = parts[0] || 'Contact';
    const messageBody = parts[1] || 'On my way, will join in 10 minutes.';
    const matchedContact = state.contacts.find((c) =>
      c.name.toLowerCase().includes(recipientRaw.toLowerCase())
    );
    const recipientLabel = matchedContact ? `${matchedContact.name} (${matchedContact.phone})` : recipientRaw;

    return {
      status: 'REQUIRES_CONFIRMATION',
      category: 'Consequential · SMS Preview',
      intentSignature: `Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", "${messageBody}")`,
      spokenResponse: `Prepared message to ${matchedContact ? matchedContact.name : recipientRaw}: "${messageBody}". Say Confirm or tap Confirm to open SMS composer.`,
      detailLog: `Holding SMS draft for explicit confirmation. Silence never counts as consent.`,
      pendingConfirmation: {
        id: `conf_sms_${Date.now()}`,
        commandCategory: 'SMS Draft Preview',
        actionSummary: `To: ${recipientLabel}`,
        previewPayload: `Message Preview: "${messageBody}"`,
        spokenPrompt: `Prepared message to ${matchedContact ? matchedContact.name : recipientRaw}: ${messageBody}. Say Confirm or Cancel.`,
        intentSignature: 'Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))',
        onConfirm: () => {
          mutators.setActiveAppTitle(`Messages · Draft to ${recipientLabel}`);
          mutators.setScreenContext(DeviceScreenContext.OTHER_APP_FOREGROUND);
          return {
            spokenResponse: `Opened SMS composer with your draft to ${matchedContact ? matchedContact.name : recipientRaw}.`,
            detailLog: `Confirmed SMS preview and launched ACTION_SENDTO composer.`,
          };
        },
      },
    };
  }

  // 12. Accessibility Automation Start/Stop
  if (normalized.includes('start accessibility') || normalized.includes('start automation')) {
    return {
      status: 'REQUIRES_CONFIRMATION',
      category: 'Consequential · Accessibility',
      intentSignature: 'JarvisAccessibilityService.startRoutine(ALLOWLISTED_NAV_SCROLL)',
      spokenResponse: 'Confirm starting allowlisted accessibility scroll automation? Say Confirm or Cancel.',
      detailLog: 'Awaiting explicit confirmation before enabling AccessibilityService routine.',
      pendingConfirmation: {
        id: `conf_a11y_${Date.now()}`,
        commandCategory: 'Accessibility Automation',
        actionSummary: 'Start Allowlisted UI Scroll Routine',
        previewPayload: 'Scope: Read current window hierarchy & perform deterministic scroll gestures',
        spokenPrompt: 'Confirm starting allowlisted accessibility automation? Say Confirm or Cancel.',
        intentSignature: 'JarvisAccessibilityService.startRoutine()',
        onConfirm: () => {
          mutators.setAccessibilityAutomationActive(true);
          return {
            spokenResponse: 'Allowlisted accessibility automation is now active.',
            detailLog: 'Started allowlisted accessibility automation routine after user confirmation.',
          };
        },
      },
    };
  }

  if (normalized.includes('stop accessibility') || normalized.includes('stop automation')) {
    mutators.setAccessibilityAutomationActive(false);
    return {
      status: 'EXECUTED',
      category: 'Accessibility · Stop',
      intentSignature: 'JarvisAccessibilityService.stopAllRoutines()',
      spokenResponse: 'Stopped accessibility automation immediately.',
      detailLog: 'Halted all active accessibility routines.',
    };
  }

  return {
    status: 'REJECTED_ALLOWLIST',
    category: 'Unrecognized / Blocked',
    intentSignature: 'ROUTER_DENY_UNLISTED',
    spokenResponse: 'Command not recognized in the deterministic allowlist. Arbitrary execution is disabled.',
    detailLog: `Rejected unlisted command "${cleaned}". Strict allowlist enforced.`,
  };
}
