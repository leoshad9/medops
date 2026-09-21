import ReactMarkdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeRaw from "rehype-raw";
import rehypeSanitize from "rehype-sanitize";

interface ReportSummaryProps {
  summary: string | null | undefined;
  className?: string;
}

const markdownComponents: Components = {
  p: ({ node: _node, ...props }) => <p {...props} className="mb-1 last:mb-0" />,
  ul: ({ node: _node, ...props }) => <ul {...props} className="mb-1 pl-5 last:mb-0" />,
  ol: ({ node: _node, ...props }) => <ol {...props} className="mb-1 pl-5 last:mb-0" />,
  li: ({ node: _node, ...props }) => <li {...props} className="mb-0.5" />,
  strong: ({ node: _node, ...props }) => <strong {...props} className="font-semibold" />,
  em: ({ node: _node, ...props }) => <em {...props} className="italic" />,
  del: ({ node: _node, ...props }) => <del {...props} className="line-through" />,
  code: ({ node: _node, ...props }) => (
    <code {...props} className="rounded bg-black/5 px-1 py-0.5 font-mono text-xs" />
  ),
  pre: ({ node: _node, ...props }) => (
    <pre {...props} className="overflow-x-auto rounded-lg bg-black/5 p-3 text-xs" />
  ),
  blockquote: ({ node: _node, ...props }) => (
    <blockquote {...props} className="border-l-2 border-brand-primary pl-3 italic" />
  ),
};

export function ReportSummary({ summary, className }: Readonly<ReportSummaryProps>) {
  if (!summary) return null;
  return (
    <div className={className}>
      <ReactMarkdown
        components={markdownComponents}
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeRaw, rehypeSanitize]}
      >
        {summary}
      </ReactMarkdown>
    </div>
  );
}
