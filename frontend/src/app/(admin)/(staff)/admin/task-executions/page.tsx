import TaskExecutionsPage from '@/_pages/admin-task-executions';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['TASK_MANAGE']}>
      <TaskExecutionsPage />
    </PermissionGate>
  );
}
