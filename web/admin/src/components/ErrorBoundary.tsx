import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
  /** Optional label shown in the fallback UI for easier debugging */
  label?: string
}

interface State {
  error: Error | null
}

/**
 * Catches render-time JS exceptions so a single broken tab/widget cannot
 * white-screen the whole page. Wrap any tab or independently-loadable section.
 *
 * Common causes of white-screen without this guard:
 *  - API returns unexpected shape (e.g. { ok:true } instead of array) and
 *    component calls .map() / .reduce() / accesses a missing nested field
 *  - Missing mock handler causes fallthrough to generic { ok:true } response
 *  - Null/undefined propagation when server is unreachable
 *
 * Rules to avoid needing this as a crutch:
 *  1. Always initialise state with the correct empty type ([] for arrays, null for objects)
 *  2. Guard array operations: `(data ?? []).map(...)` not `data.map(...)`
 *  3. Guard nested access: `data?.lines ?? []` not `data.lines`
 *  4. Add a mock handler for EVERY new API endpoint in mock.ts immediately
 *  5. Wrap every new page's tab-pane content in <ErrorBoundary label="TabName">
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(`[ErrorBoundary${this.props.label ? ` "${this.props.label}"` : ''}]`, error, info.componentStack)
  }

  reset = () => this.setState({ error: null })

  render() {
    if (this.state.error) {
      return (
        <div className="flex flex-col items-center justify-center py-20 gap-4 text-center">
          <div className="text-4xl">⚠️</div>
          <p className="text-gray-600 font-medium">
            {this.props.label ? `"${this.props.label}" ` : ''}加载失败
          </p>
          <p className="text-xs text-gray-400 font-mono max-w-sm break-all">
            {this.state.error.message}
          </p>
          <button
            onClick={this.reset}
            className="px-4 py-2 text-sm rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-700"
          >
            重试
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
