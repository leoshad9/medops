import type { ApiResponse } from "../types/api";
import { api } from "./api";

/** A follow-up chip the assistant offered. */
export type AISuggestion = string | { label?: string; query?: string };

export interface AIMessage {
  id: string;
  role: "assistant" | "user";
  content: string;
  timestamp: string;
  /**
   * Follow-up chips the backend extracted from the reply. Carried on the
   * message rather than re-parsed from `content`: the server already returns
   * them as a separate field and strips the JSON block, so nothing in the
   * rendered text refers to them.
   */
  suggestions?: AISuggestion[];
}

export interface AIChatResponse {
  message: AIMessage;
  raw?: {
    actions?: Array<{ id: string | null; resolved?: any; label?: string | null } | null> | null;
    suggestions?: AISuggestion[] | null;
  } | null;
}

/** Wire format returned by `POST /api/v1/assistant/chat`. */
interface AssistantChatResponseDto {
  message: string;
  actions?: Array<{ id: string | null; resolved?: any; label?: string | null } | null> | null;
  suggestions?: AISuggestion[] | null;
}

export interface AIProfile {
  name?: string;
  email?: string;
  phone?: string;
  dateOfBirth?: string;
  gender?: string;
  address?: string;
  insuranceProvider?: string;
  insuranceMemberId?: string;
}

/**
 * Sends a chat message to the MedOps assistant API and resolves with the reply.
 *
 * Identity is derived server-side from the authenticated session; this call
 * intentionally carries nothing but the message text and prior turns. The optional
 * `timeZone` is the caller's IANA zone (e.g. `Intl.DateTimeFormat().resolvedOptions().timeZone`);
 * the backend renders appointment times in it. When omitted the server falls back to UTC.
 *
 * :param history: prior turns, oldest first, excluding the message being sent.
 *   Sent so the assistant can answer follow-ups in context; the server caps and
 *   validates them.
 * :param profile: optional read-only snapshot of the signed-in user's profile.
 *   When provided, the assistant can answer profile questions from it.
 *
 * :throws: when the API is unreachable or the assistant backend fails —
 *  callers show a user-facing error (see MedOpsAIChatPanel).
 */
export async function getAIResponse(
  query: string,
  timeZone?: string,
  history?: AIMessage[],
  profile?: AIProfile,
): Promise<AIChatResponse> {
  const conversationHistory = (history ?? [])
    .filter((m) => m.role === "user" || m.role === "assistant")
    .map((m) => ({ role: m.role, content: m.content }));

  const response = await api.post<ApiResponse<AssistantChatResponseDto>>(
    "/v1/assistant/chat",
    {
      message: query,
      ...(timeZone ? { timeZone } : {}),
      ...(conversationHistory.length ? { conversationHistory } : {}),
      ...(profile ? { profile } : {}),
    },
  );

  // Keep the raw actions array for the chat panel to populate the resolver.
  const dto = response.data.data;

  return {
    message: {
      id: crypto.randomUUID(),
      role: "assistant",
      content: dto.message,
      timestamp: new Date().toISOString(),
      // Only set when present, so an absent field does not become an empty
      // array that would render a stray chip row.
      ...(dto.suggestions?.length ? { suggestions: dto.suggestions } : {}),
    },
    // expose the raw response for caller use (non-serialised)
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    raw: dto,
  } as unknown as AIChatResponse;
}
