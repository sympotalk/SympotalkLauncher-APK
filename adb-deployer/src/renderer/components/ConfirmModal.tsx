// 위험 명령 실행 전 확인 다이얼로그
interface Props {
  title: string
  message: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmModal({ title, message, onConfirm, onCancel }: Props) {
  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <h2 className="modal__title">⚠ {title}</h2>
        <p className="modal__message">{message}</p>
        <div className="modal__actions">
          <button className="btn btn-secondary" onClick={onCancel}>취소</button>
          <button className="btn btn-danger" onClick={onConfirm}>실행</button>
        </div>
      </div>
    </div>
  )
}
