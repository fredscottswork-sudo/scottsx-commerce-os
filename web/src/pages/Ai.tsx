import { AiConsole } from '../components/AiConsole';
import { useSeo } from '../hooks/useSeo';

/** Public AI entry point — anonymous shoppers get the same grounded engine. */
export default function Ai() {
  useSeo({
    title: 'AI shopping assistant',
    description:
      'Ask the ScottsTechX assistant to find products, compare prices and ' +
      'recommend sellers across the whole marketplace.',
  });

  // No PageHeader here. Its title and subtitle sat ABOVE the chat and ate
  // ~90px of vertical space on a phone before the conversation even started.
  // The same words now live inside the chat's own header and welcome panel,
  // so the card gets the full height of the screen.
  return (
    <AiConsole
      audience="buyer"
      screen="web-public-ai"
      title="AI shopper"
      subtitle="Ask anything about the store — no account needed. Tell the assistant what you need and your budget, and it searches the whole catalogue."
      fullHeight
    />
  );
}
