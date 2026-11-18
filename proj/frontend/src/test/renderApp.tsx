import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import App from "../App";

/** Renders the whole app at a URL, so tests can follow navigation like a user would. */
export function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}