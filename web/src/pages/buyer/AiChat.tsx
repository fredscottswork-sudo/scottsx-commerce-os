import { AiConsole } from '../../components/AiConsole';
import { PageHeader } from '../../components/ui';

export default function AiChat() {
  return (
    <>
      <PageHeader
        title="AI shopper"
        sub="Grounded in the live catalogue — it can see every approved product, price and seller."
      />
      <AiConsole
        audience="buyer"
        screen="web-buyer-ai"
        title="Your personal shopping agent"
        subtitle="Describe what you need in plain language. Ask for comparisons, budgets, or the best deal — the assistant reads the real store."
      />
    </>
  );
}
