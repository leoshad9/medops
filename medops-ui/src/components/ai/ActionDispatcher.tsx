import { useNavigate } from "react-router-dom";

// Interpret resolved action payloads and perform app-specific behaviors.
export function ActionDispatcher() {
  const navigate = useNavigate();

  // Expose a global dispatch entrypoint for tests and ad-hoc UI hooks.
  // The dispatch accepts the resolved payload returned by the server.
  (window as any).__medops_dispatch_action = (resolved: any) => {
    if (!resolved || typeof resolved !== "object") return false;
    const t = resolved.type;
    if (t === "navigate") {
      const route = resolved.route || "/";
      const query = resolved.query ? `?q=${encodeURIComponent(JSON.stringify(resolved.query))}` : "";
      navigate(`${route}${query}`);
      return true;
    }
    if (t === "modal") {
      // In this app, modals are opened via custom event listening.
      window.dispatchEvent(new CustomEvent("medops:open-modal", { detail: resolved }));
      return true;
    }
    if (t === "api-call") {
      // Fire-and-forget API action; the caller should handle success/failure UI.
      fetch(resolved.api_path, {
        method: resolved.method || "POST",
        headers: { "Content-Type": "application/json" },
        body: resolved.body ? JSON.stringify(resolved.body) : undefined,
      });
      return true;
    }
    return false;
  };

  return null;
}
