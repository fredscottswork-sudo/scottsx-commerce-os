import { Link } from 'react-router-dom';
import { Btn } from '../components/ui';

export default function NotFound() {
  return (
    <div className="center-box">
      <div className="big">🧭</div>
      <strong>Page not found</strong>
      <span>That page doesn't exist or you don't have access to it.</span>
      <Link to="/"><Btn variant="primary">Go to marketplace</Btn></Link>
    </div>
  );
}
