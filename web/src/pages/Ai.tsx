import { AiConsole } from '../components/AiConsole';
import { PageHeader } from '../components/ui';

/** Public AI entry point — anonymous shoppers get the same grounded engine. */
export default function Ai() {
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
        subtitle="Tell the assistant what you need and your budget. It searches the whole ScottsTechX catalogue and explains its picks."
      />
    </>
  );
}
