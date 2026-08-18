import { AiConsole } from '../../components/AiConsole';

export default function AiChat() {
  return (
    <AiConsole
      audience="buyer"
      screen="web-buyer-ai"
      title="AI shopper"
      subtitle="Describe what you need in plain language — comparisons, budgets or the best deal. Grounded in the live catalogue: every approved product, price and seller."
      fullHeight
    />
  );
}
