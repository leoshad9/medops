import { createRoot } from "react-dom/client";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { ActionDispatcher } from "../../components/ai/ActionDispatcher";

test("dispatch navigate uses history", async () => {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);

  root.render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/" element={<ActionDispatcher />} />
        <Route path="/appointments" element={<div data-testid="appts">OK</div>} />
      </Routes>
    </MemoryRouter>,
  );

  // allow the component to mount and set the global
  await new Promise((r) => setTimeout(r, 10));

  // call the global dispatch with a navigate payload
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore
  const fn = (window as any).__medops_dispatch_action;
  expect(typeof fn).toBe("function");
  const ok = fn({ type: "navigate", route: "/appointments", query: { index: 0 } });
  expect(ok).toBe(true);
  root.unmount();
  container.remove();
});
