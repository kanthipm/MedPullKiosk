import { NavLink, Outlet } from 'react-router-dom'
import { longDate } from '../../lib/format'
import NotificationsPopover from '../NotificationsPopover'

const navLink = ({ isActive }: { isActive: boolean }) =>
  `rounded-[10px] px-3 py-1.5 text-[13px] font-extrabold transition-all duration-200 ${
    isActive ? 'bg-white text-ink shadow-segment' : 'text-muted hover:text-ink'
  }`

export default function AppShell() {
  return (
    <div className="min-h-screen">
      <header className="glass sticky top-0 z-20 border-x-0 border-t-0 shadow-none">
        <div className="mx-auto flex h-[58px] max-w-5xl items-center gap-5 px-6">
          <NavLink to="/" className="flex items-center gap-2.5">
            <span
              aria-hidden
              className="grid h-8 w-8 place-items-center rounded-[11px] bg-gradient-to-br from-oxy to-oxy-light text-[15px] font-black leading-none text-white shadow-[0_6px_16px_rgba(47,128,237,.35)]"
            >
              +
            </span>
            <span className="text-sm font-black tracking-tight text-ink">
              MedPull
              <span className="ml-2 font-bold text-muted">Recovery Copilot</span>
            </span>
          </NavLink>
          <nav className="ml-1 flex items-center gap-1 rounded-[13px] bg-[#e9edf6]/70 p-[3px]">
            <NavLink to="/" end className={navLink}>
              Worklist
            </NavLink>
            <NavLink to="/integrations" className={navLink}>
              Integrations
            </NavLink>
            <NavLink to="/settings/notifications" className={navLink}>
              Settings
            </NavLink>
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <span className="hidden text-xs font-bold text-faint sm:block">{longDate()}</span>
            <NotificationsPopover />
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  )
}
