import { AiConsole } from '../../components/AiConsole';

export default function SellerAi() {
  return (
    <AiConsole
      audience="seller"
      screen="web-seller-ai"
      title="AI copilot"
      subtitle="Ask how to price against competitors, what to stock next, or how to reply to a hesitant buyer. Grounded in your real store data."
      fullHeight
    />
  );
}
