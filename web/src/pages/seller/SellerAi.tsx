import { AiConsole } from '../../components/AiConsole';
import { PageHeader } from '../../components/ui';

export default function SellerAi() {
  return (
    <>
      <PageHeader
        title="AI copilot"
        sub="Listing help, pricing intel and buyer-reply drafts — grounded in your real store data."
      />
      <AiConsole
        audience="seller"
        screen="web-seller-ai"
        title="Grow your store with AI"
        subtitle="Ask how to price against competitors, what to stock next, or how to reply to a hesitant buyer. The copilot sees your catalogue and the wider market."
      />
    </>
  );
}
