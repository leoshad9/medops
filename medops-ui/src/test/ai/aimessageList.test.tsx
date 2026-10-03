import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

import { AIMessageList } from "../../components/ai/AIMessageList";
import type { AIMessage } from "../../services/aiAssistantService";

describe("AIMessageList", () => {
  it("renders Markdown bold as <strong> instead of literal asterisks", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "**Doctor:** Dr. Mohd Adnan",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toMatch(/<strong[^>]*>Doctor:<\/strong>/);
    expect(html).not.toContain("**Doctor:**");
  });

  it("renders Markdown bullet lists as <ul>/<li> with bold items", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "- **Doctor:** Dr. Mohd Adnan\n- **Location:** General OPD, Ground Floor\n- **Status:** Booked",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("<ul");
    expect(html).toContain("<li");
    expect(html).toMatch(/<strong[^>]*>Doctor:<\/strong>/);
    expect(html).not.toContain("**Doctor:**");
  });

  it("renders GFM strikethrough as <del> instead of literal tildes", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "~~Cancelled~~ Appointment confirmed",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("<del");
    expect(html).not.toContain("~~Cancelled~~");
  });

  it("renders GFM tables correctly without leaking pipe characters", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "| Doctor | Location |\n|--------|----------|\n| Dr. Mohd Adnan | General OPD |",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("<table");
    expect(html).toContain("<th");
    expect(html).toContain("<td");
    expect(html).toContain("Dr. Mohd Adnan");
    expect(html).not.toMatch(/\|\s*Doctor\s*\|\s*Location/);
  });

  it("renders inline HTML from AI responses safely", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "Patient: John Doe<br>Age: 45",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("John Doe");
    expect(html).toContain("Age: 45");
    expect(html).toMatch(/<br\s*\/?>/);
  });

  it("sanitizes unsafe attributes and tags from inline HTML", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content:
          'Safe <strong>text</strong><br><span style="color: red" onclick="alert(1)">unsafe</span><script>alert(2)</script>',
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toMatch(/<strong[^>]*>text<\/strong>/);
    expect(html).toMatch(/<br\s*\/?>/);
    expect(html).not.toContain("style=");
    expect(html).not.toContain("onclick=");
    expect(html).not.toContain("<script");
    expect(html).not.toContain("alert(1)");
    expect(html).not.toContain("alert(2)");
  });

  it("renders bold text inside sentences without trailing asterisks", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "Your next appointment is with **Dr. Mohd Adnan** on **September 25**.",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toMatch(/<strong[^>]*>Dr\. Mohd Adnan<\/strong>/);
    expect(html).toMatch(/<strong[^>]*>September 25<\/strong>/);
    expect(html).not.toContain("**Dr. Mohd Adnan**");
    expect(html).not.toContain("**September 25**");
  });

  it("shows the topic chips on arrival but not 'Back to main'", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "Hello! How can I help?",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("Appointments");
    expect(html).toContain("Using MedOps");
    // Nothing to return from yet, so the control would be dead weight.
    expect(html).not.toContain("Back to main");
  });

  it("replaces the topic chips with 'Back to main' once the patient has asked something", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "Hello! How can I help?",
        timestamp: "2026-09-20T00:00:00Z",
      },
      {
        id: "2",
        role: "user",
        content: "I need help with my upcoming appointments.",
        timestamp: "2026-09-20T00:00:10Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("Back to main");
    expect(html).not.toContain("What can I help you with?");
    expect(html).not.toContain("Using MedOps");
  });

  it("disables 'Back to main' until the assistant reply has finished rendering", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "Hello! How can I help?",
        timestamp: "2026-09-20T00:00:00Z",
      },
      {
        id: "2",
        role: "user",
        content: "I need help with my upcoming appointments.",
        timestamp: "2026-09-20T00:00:10Z",
      },
    ];

    // The chip swaps to "Back to main" the instant the question is sent, so the
    // control was live while the reply was still in flight. Clearing the
    // conversation then left the pending reply to land on an empty transcript.
    const pending = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} isPending />
    );
    const settled = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(pending).toMatch(/<button[^>]*\sdisabled=""[^>]*>[\s\S]*?Back to main/);
    expect(settled).not.toMatch(/\sdisabled=""/);
  });

  it("disables the suggestion chips of the message being answered", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "Your invoice is paid.",
        suggestions: ["Show my lab reports", { label: "View prescriptions", query: "prescriptions" }],
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const pending = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} isPending />
    );

    expect(pending).toMatch(/<button[^>]*\sdisabled=""[^>]*>[\s\S]*?Show my lab reports/);
    expect(pending).toMatch(/<button[^>]*\sdisabled=""[^>]*>[\s\S]*?View prescriptions/);
  });

  it("renders suggestions from the message field, not from the reply text", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "Your invoice is paid.",
        suggestions: ["Show my lab reports", { label: "View prescriptions", query: "prescriptions" }],
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    const html = renderToStaticMarkup(
      <AIMessageList messages={messages} onQuickAction={() => {}} />
    );

    expect(html).toContain("Show my lab reports");
    expect(html).toContain("View prescriptions");
    // The backend strips the JSON block, so it must never reach the bubble.
    expect(html).not.toContain("suggestions");
    expect(html).not.toContain("```");
  });

it("does not invent chips from a JSON block left in the reply text", () => {
    const messages: AIMessage[] = [
      {
        id: "1",
        role: "assistant",
        content: "Here you go.",
        timestamp: "2026-09-20T00:00:00Z",
      },
    ];

    // No suggestions field, but the text still carries a fenced block. Before,
    // renderSuggestions regex-matched the content and built a chip from it.
    const html = renderToStaticMarkup(
      <AIMessageList
        messages={messages.map((m) => ({ ...m, content: `${m.content}\n\n\`\`\`json\n{"suggestions": ["Leaked chip"]}\n\`\`\`` }))}
        onQuickAction={() => {}}
      />
    );

    // Stripping is validate_reply's job on the server. Here we only assert the UI
    // no longer mines the text for chips: the block still renders as prose, but
    // no suggestion button is created from it.
    expect(html).not.toContain("suggestion_");
    expect(html).not.toMatch(/<button[^>]*>[^<]*Leaked chip/);
  });
});
