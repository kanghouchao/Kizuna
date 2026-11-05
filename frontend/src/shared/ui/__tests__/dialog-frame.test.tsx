import { fireEvent, render, screen } from '@testing-library/react';
import { Button } from '../button';
import { Dialog } from '../dialog';
import { DialogFrame, DialogFrameActions } from '../dialog-frame';

it('題名と説明でダイアログが名付けられ、本文と操作帯が描かれる', () => {
  render(
    <Dialog open>
      <DialogFrame
        size="sm"
        title="ポイントを調整"
        description="調整の理由は履歴に残ります。"
        footer={<Button>保存する</Button>}
      >
        <p>現在の残高 120pt</p>
      </DialogFrame>
    </Dialog>
  );
  const dialog = screen.getByRole('dialog', { name: 'ポイントを調整' });
  expect(dialog).toHaveAccessibleDescription('調整の理由は履歴に残ります。');
  expect(dialog).toHaveTextContent('現在の残高 120pt');
  expect(screen.getByRole('button', { name: '保存する' })).toBeInTheDocument();
});

it('form を渡すと操作帯の送信ボタンで本文の入力ごと送信される', () => {
  const onSubmit = jest.fn((event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    return new FormData(event.currentTarget).get('reason');
  });
  render(
    <Dialog open>
      <DialogFrame
        size="sm"
        title="理由の入力"
        form={{ onSubmit }}
        footer={<Button type="submit">送信する</Button>}
      >
        <input name="reason" aria-label="理由" defaultValue="誤登録" />
      </DialogFrame>
    </Dialog>
  );
  fireEvent.click(screen.getByRole('button', { name: '送信する' }));
  expect(onSubmit).toHaveReturnedWith('誤登録');
});

it.each([
  ['sm', 'sm:max-w-md'],
  ['md', 'sm:max-w-2xl'],
  ['lg', 'sm:max-w-4xl'],
] as const)('幅の段 %s は %s（28 / 42 / 56rem）だけを効かせる', (size, expected) => {
  render(
    <Dialog open>
      <DialogFrame size={size} title="幅">
        本文
      </DialogFrame>
    </Dialog>
  );
  const dialog = screen.getByRole('dialog', { name: '幅' });
  expect(dialog).toHaveClass(expected);
  expect(dialog).not.toHaveClass('sm:max-w-lg');
});

it('本文の部品が DialogFrameActions に置いた操作は操作帯に出て、form 属性で本文の form を送る', () => {
  const onSubmit = jest.fn((event: React.FormEvent<HTMLFormElement>) => event.preventDefault());
  function Step() {
    return (
      <form id="step-form" onSubmit={onSubmit}>
        <input aria-label="理由" />
        <DialogFrameActions>
          <Button type="submit" form="step-form">
            差し引く
          </Button>
        </DialogFrameActions>
      </form>
    );
  }
  render(
    <Dialog open>
      <DialogFrame size="sm" title="差し引き">
        <Step />
      </DialogFrame>
    </Dialog>
  );
  const button = screen.getByRole('button', { name: '差し引く' });
  expect(button.closest('[data-slot="dialog-footer"]')).not.toBeNull();
  expect(button.closest('form')).toBeNull();
  fireEvent.click(button);
  expect(onSubmit).toHaveBeenCalledTimes(1);
});

it('footer の操作の後ろ（主操作の位置）に本文の部品の操作が並ぶ', () => {
  render(
    <Dialog open>
      <DialogFrame size="sm" title="並び" footer={<Button>閉じる</Button>}>
        <DialogFrameActions>
          <Button>保存する</Button>
        </DialogFrameActions>
      </DialogFrame>
    </Dialog>
  );
  const names = screen
    .getAllByRole('button')
    .filter(button => button.closest('[data-slot="dialog-footer"]'))
    .map(button => button.textContent);
  expect(names).toEqual(['閉じる', '保存する']);
});
