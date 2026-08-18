import { AiConsole } from '../components/AiConsole';
import { PageHeader } from '../components/ui';
import { useSeo } from '../hooks/useSeo';

/** Public AI entry point — anonymous shoppers get the same grounded engine. */
export default function Ai() {
  useSeo({
    title: 'AI shopping assistant',
    description:
      'Ask the ScottsTechX assistant to find products, compare prices and ' +
      'recommend sellers across the whole marketplace.',
  });

  return (
    <>
      <PageHeader
        title="AI shopper"
        sub="Ask anything about the store. No account needed."
      />
      <AiConsole
        audience="buyer"
        screen="web-public-ai"
        title="Shop by conversation"
        subtitle="Tell the assistant what you need and your budget — it searches the whole catalogue and explains its picks."
      />
    </>
  );
}
