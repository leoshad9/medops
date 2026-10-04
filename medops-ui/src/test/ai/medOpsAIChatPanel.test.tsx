import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { MedOpsAIChatPanel } from "../../components/ai/MedOpsAIChatPanel";

// The panel only calls the service when a question is sent, so the module is stubbed
// to keep a stray request from ever leaving the test.
vi.mock("../../services/aiAssistantService", () => ({
  getAIResponse: vi.fn(),
}));

const STALE_LINE = "Stale question left over from the last visit";
const STORED_CONVERSATION = [
  {
    id: "old-1",
    role: "user",
    content: STALE_LINE,
    timestamp: "2026-09-20T00:00:00Z",
  },
];

let container: HTMLDivElement;
let root: Root;

const renderPanel = async () => {
  await act(async () => {
    root.render(
      <MemoryRouter>
        <MedOpsAIChatPanel isOpen onClose={() => {}} firstName="Ada" />
      </MemoryRouter>,
    );
  });
};

const clickResetChat = async () => {
  const reset = Array.from(container.querySelectorAll("button")).find(
    (button) => button.textContent?.trim() === "Reset chat",
  );

  expect(reset).toBeDefined();

  await act(async () => {
    reset?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
};

beforeEach(() => {
  (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
  localStorage.clear();
  sessionStorage.clear();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => {
    root.unmount();
  });
  container.remove();
});

describe("MedOpsAIChatPanel conversation storage", () => {
  it("starts a new visit from a clean slate and discards the stored transcript", async () => {
    localStorage.setItem("medops_ai_conv", JSON.stringify(STORED_CONVERSATION));
    // No visit marker: this is the first mount of a fresh browser session.

    await renderPanel();

    expect(container.textContent).toContain("How can I help you today?");
    expect(container.textContent).not.toContain(STALE_LINE);
    expect(localStorage.getItem("medops_ai_conv")).toBe("[]");
  });

  it("restores the transcript when the panel remounts within the same visit", async () => {
    sessionStorage.setItem("medops_ai_chat_visit", "1");
    localStorage.setItem("medops_ai_conv", JSON.stringify(STORED_CONVERSATION));

    await renderPanel();

    expect(container.textContent).toContain(STALE_LINE);
  });

  it("clears the transcript when 'Reset chat' is pressed", async () => {
    sessionStorage.setItem("medops_ai_chat_visit", "1");
    localStorage.setItem("medops_ai_conv", JSON.stringify(STORED_CONVERSATION));

    await renderPanel();
    expect(container.textContent).toContain(STALE_LINE);

    await clickResetChat();

    expect(container.textContent).not.toContain(STALE_LINE);
    expect(localStorage.getItem("medops_ai_conv")).toBe("[]");
  });
});
