import { beforeEach, describe, expect, it, vi } from "vitest";

import { api } from "../../services/api";
import { getAIResponse } from "../../services/aiAssistantService";

// The real axios client wires CSRF cookies and a 401-retry interceptor; none of
// that is relevant here. Replace it with a stub that records the request body.
vi.mock("../../services/api", () => ({
  api: {
    post: vi.fn(),
  },
}));

const mockedPost = api.post as unknown as ReturnType<typeof vi.fn>;

describe("getAIResponse", () => {
  beforeEach(() => {
    mockedPost.mockReset();
    mockedPost.mockResolvedValue({
      data: { data: { message: "reply" } },
    });
  });

  it("sends the caller's IANA timeZone in the chat request body", async () => {
    await getAIResponse("When is my next appointment?", "Asia/Kolkata");

    expect(mockedPost).toHaveBeenCalledWith(
      "/v1/assistant/chat",
      expect.objectContaining({
        message: "When is my next appointment?",
        timeZone: "Asia/Kolkata",
      }),
    );
  });

  it("omits timeZone from the body when the caller does not supply one", async () => {
    await getAIResponse("hello");

    expect(mockedPost).toHaveBeenCalledWith("/v1/assistant/chat", { message: "hello" });
  });

  it("sends profile in the request body when provided", async () => {
    const profile = {
      name: "John Doe",
      email: "john@example.com",
      phone: "123-456-7890",
      dateOfBirth: "1990-01-01",
      gender: "Male",
      address: "123 Main St",
      insuranceProvider: "Health Insurance Co",
      insuranceMemberId: "MEM123456",
    };

    await getAIResponse("What is my name?", undefined, undefined, profile);

    expect(mockedPost).toHaveBeenCalledWith(
      "/v1/assistant/chat",
      expect.objectContaining({
        message: "What is my name?",
        profile,
      }),
    );
  });

  it("omits profile from the body when not provided", async () => {
    await getAIResponse("hello");

    expect(mockedPost).toHaveBeenCalledWith(
      "/v1/assistant/chat",
      expect.objectContaining({
        message: "hello",
      }),
    );

    const callArgs = mockedPost.mock.calls[0];
    expect(callArgs[1]).not.toHaveProperty("profile");
  });
});