import type { ReactNode } from "react";
import { Logo } from "./Logo";

/** Logo above one white card with the page title: shared by registration and login. */
export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="page">
      <Logo />
      <section className="card" aria-labelledby="page-title">
        <h1 id="page-title">{title}</h1>
        {children}
      </section>
    </main>
  );
}