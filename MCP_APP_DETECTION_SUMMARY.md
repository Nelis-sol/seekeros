# MCP App Detection Implementation Summary

## Overview
Extended the existing installed app detection system to also detect and suggest MCP (Model Context Protocol) apps while the user types in the chat interface.

## What Was Implemented

### 1. Detection Logic
**File**: `MainActivity.kt`

#### Enhanced `detectAppsInText()`:
- Now detects both installed apps AND MCP apps simultaneously
- Shows combined suggestions when either type is detected
- Falls back to intent detection if no apps are found

#### New `detectMcpAppsInText()`:
- Searches all available MCP apps from `AppDirectory.getFeaturedApps()`
- Performs two-stage matching:
  1. **Exact app name match** (complete word): e.g., "Pizzaz", "Weather", "Calendar"
  2. **Keyword match** (if no exact match): Extracts keywords from app name and description
- Returns list of matching MCP apps

#### New `extractKeywords()`:
- Extracts relevant keywords from MCP app names and descriptions
- Supports common domain-specific words:
  - Pizza-related: "pizza", "pizzas", "food", "order", "ordering"
  - Weather-related: "weather", "forecast", "temperature", "climate"
  - Task-related: "todo", "task", "tasks", "reminder", "reminders"
  - Calendar-related: "calendar", "event", "events", "schedule", "meeting"
  - Calculator-related: "calculator", "calculate", "math", "equation"

### 2. UI Components

#### New `showCombinedAppSuggestions()`:
- Displays both installed apps and MCP apps in the suggestion pills area
- Shows installed apps first, then MCP apps
- Handles single app case with all intents shown

#### New `createMcpAppPill()`:
- Creates suggestion pill for MCP apps
- Shows app icon (loaded from URL or placeholder)
- Different styling for connected vs. unconnected apps:
  - **Unconnected**: Purple background (`#4A3A6C`)
  - **Connected**: Green background with checkmark (`#2E7D32`)
- Click behavior:
  - If connected: Shows tool pills for that app
  - If not connected: Shows connection dialog

#### New `showMcpAppConnectionDialog()`:
- Shows dialog with app name, description
- Three options:
  - **Connect**: Initiates connection to the MCP app
  - **Details**: Opens `McpAppDetailsActivity` for more info
  - **Cancel**: Dismisses dialog

### 3. Inline Pills (Text Highlighting)

#### Enhanced `applyInlinePills()`:
- Now detects MCP app names in addition to installed apps
- Applies clickable styled spans to both types
- Uses different visual styling for MCP apps vs installed apps

#### New `createMcpPillSpan()`:
- Custom `ReplacementSpan` for rendering MCP app pills inline
- Visual styling:
  - **Unconnected MCP apps**: Purple background (`#4A3A6C`)
  - **Connected MCP apps**: Green background (`#2E7D32`)
- Includes icon and bold white text
- Clickable to show suggestions

### 4. Drawable Resources

Created two new pill background drawables:

**`pill_background_mcp.xml`**:
- Purple color scheme for unconnected MCP apps
- Background: `#4A3A6C`
- Border: `#6A4A8C`
- 12dp corner radius

**`pill_background_connected.xml`**:
- Green color scheme for connected apps
- Background: `#2E7D32`
- Border: `#388E3C`
- 12dp corner radius

## How It Works

### User Types in Chat

1. **Text Change Detection**:
   - `TextWatcher` on `messageInput` triggers on text change
   - Checks word boundaries (spaces, punctuation)

2. **App Detection**:
   - Searches for installed app names
   - Searches for MCP app names (exact or keyword match)
   - Falls back to intent detection if nothing found

3. **Suggestion Pills**:
   - Shows horizontal scrollable pills below input
   - Mixed: installed apps + MCP apps
   - Color-coded: grey (installed), purple (MCP unconnected), green (MCP connected)

4. **Inline Pills**:
   - Highlights app names in text with custom styling
   - Clickable to show suggestions
   - Visual distinction between app types

### Example Scenarios

**User types: "I want pizza"**
- Detects "pizza" keyword
- Shows "Pizzaz Demo" MCP app pill
- "pizza" is highlighted in purple inline pill
- Click shows connection dialog

**User types: "Check the weather"**
- Detects "weather" keyword
- Shows "Weather" MCP app pill
- "weather" is highlighted in purple inline pill

**User types: "Open Calendar and add task"**
- Detects "Calendar" (exact name) and "task" (keyword)
- Shows both "Calendar" and "Tasks" MCP app pills
- Both words highlighted inline

**User types: "Pizzaz show me maps"**
- If connected: "Pizzaz" shown in green, clicking shows tool pills
- If not connected: "Pizzaz" shown in purple, clicking shows connection dialog

## Integration Points

### Existing Features:
- ✅ Works alongside installed app detection
- ✅ Respects MCP app context (`currentMcpAppContext`)
- ✅ Integrates with browser visibility state
- ✅ Uses existing word boundary detection
- ✅ Follows same UX patterns as installed apps

### Future Enhancements:
- Icon caching for MCP apps
- Fuzzy matching for app names
- User preferences for favorite MCP apps
- Recently used MCP apps prioritization
- ML-based intent detection for better keyword matching

## Testing Checklist

- [ ] Type "pizza" → Pizzaz Demo appears
- [ ] Type "weather" → Weather app appears
- [ ] Type "todo" or "task" → Tasks app appears
- [ ] Type "calendar" → Calendar app appears
- [ ] Type "calculator" → Calculator app appears
- [ ] Click unconnected MCP app pill → Shows connection dialog
- [ ] Click connected MCP app pill → Shows tool pills
- [ ] Inline pills render correctly with purple/green colors
- [ ] Mixed suggestions (installed + MCP) display correctly
- [ ] Word boundary detection works (no partial matches)

