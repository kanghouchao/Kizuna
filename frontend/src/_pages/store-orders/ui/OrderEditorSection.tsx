import type { ReactNode } from 'react';

export function OrderEditorSection({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <section className="grid gap-6 border-b p-6 xl:grid-cols-[10rem_minmax(0,1fr)] xl:gap-8">
      <div>
        <h2 className="font-semibold text-foreground">{title}</h2>
        <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{description}</p>
      </div>
      <div className="min-w-0 space-y-6">{children}</div>
    </section>
  );
}
