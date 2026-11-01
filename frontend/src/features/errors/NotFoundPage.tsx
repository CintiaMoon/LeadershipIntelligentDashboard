import { Link } from 'react-router-dom';
import { EmptyState } from '@/components/ui/StateViews';

export function NotFoundPage() {
  return (
    <EmptyState
      title="Page not found"
      description="The page does not exist, or you do not have access to it."
      action={
        <Link className="text-sm font-medium text-accent underline" to="/dashboard">
          Back to the dashboard
        </Link>
      }
    />
  );
}
