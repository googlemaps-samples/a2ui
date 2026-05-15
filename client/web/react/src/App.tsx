import { useState, useRef, useEffect } from 'react'
import './App.css'
import { A2UIClient, A2UIRenderer, type TimelineItem, themeStyleSheet } from '@googlemaps/a2ui/lit';

/**
 * Main Application component that demonstrates A2UI integration in a React environment.
 * It manages a chat interface with a timeline of text messages and A2UI interactive surfaces.
 */
function App() {
  // --- UI State ---
  const [isChatOpen, setIsChatOpen] = useState(true)
  const [timeline, setTimeline] = useState<TimelineItem[]>([])
  const [input, setInput] = useState('')
  const [isRequesting, setIsRequesting] = useState(false)
  const [importJson, setImportJson] = useState('')
  const importDialogRef = useRef<HTMLDialogElement>(null)
  const [lastResponseJson, setLastResponseJson] = useState('')

  // --- A2UI Integration Refs ---
  // A2UIClient handles communication with the A2A agent
  const clientRef = useRef(new A2UIClient())
  // A2UIRenderer manages the local state of A2UI surfaces and message processing
  const rendererRef = useRef(new A2UIRenderer())

  // Handle scrolling properly.
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }
  useEffect(() => {
    scrollToBottom()
  }, [timeline])

  useEffect(() => {
    if (!document.adoptedStyleSheets.includes(themeStyleSheet)) {
      document.adoptedStyleSheets = [...document.adoptedStyleSheets, themeStyleSheet];
    }
  }, []);

  const handleImport = () => {
    try {
      const messages = JSON.parse(importJson);
      rendererRef.current = new A2UIRenderer();
      rendererRef.current.processResponse(messages.map((msg: any) => ({ type: "a2ui", message: msg })));
      setTimeline([...rendererRef.current.timeline]);
      importDialogRef.current?.close();
      setImportJson('');
    } catch (e) {
      alert("Failed to parse JSON: " + e);
    }
  };

  /**
   * Handles sending a text message from the user.
   * Updates the UI timeline and processes the agent's response.
   */
  const handleSend = async () => {
    if (!input.trim() || isRequesting) return

    const messageText = input.trim()
    setInput('')
    setIsRequesting(true)

    // 1. Add the user's message to the local renderer's timeline
    rendererRef.current.addUserMessage(messageText)
    setTimeline([...rendererRef.current.timeline])

    try {
      // 2. Send the message to the A2A agent via A2UIClient
      const response = await clientRef.current.send(messageText)

      // 3. Process the response (which may contain text and/or A2UI data)
      rendererRef.current.processResponse(response)

      // Update last response JSON
      const uiMessages = response.filter((p: any) => p.type === "a2ui").map((p: any) => p.message);
      if (uiMessages.length > 0) {
        setLastResponseJson(JSON.stringify(uiMessages, null, 2));
      }

      // 4. Synchronize the React state with the renderer's updated timeline
      setTimeline([...rendererRef.current.timeline])
    } catch (error) {
      console.error("Failed to send message:", error)
      rendererRef.current.processResponse([{
        type: 'text',
        text: `Error: ${error instanceof Error ? error.message : 'Unknown error'}`
      }])
      setTimeline([...rendererRef.current.timeline])
    } finally {
      setIsRequesting(false)
    }
  }

  return (
    <div className="app-container">
      {/* --- Main Content Panel --- */}
      <main className="main-panel">
        {!isChatOpen && (
          <button
            className="toggle-chat-btn"
            onClick={() => setIsChatOpen(true)}
          >
            Open Chat
          </button>
        )}
        <div className="main-panel-content">
          <h1>Main content</h1>
        </div>
      </main>

      {/* --- Side Chat Panel --- */}
      <aside className={`chat-panel ${isChatOpen ? 'open' : 'closed'}`}>
        <div className="chat-header">
          <h2>Chat</h2>

          <button
            className="close-chat-btn"
            onClick={() => setIsChatOpen(false)}
          >
            ×
          </button>
        </div>

        {/* --- Message Timeline --- */}
        <div className="chat-messages">
          <maui-providers>
            {timeline.length === 0 && (
              <p style={{ opacity: 0.5, textAlign: 'center', marginTop: '50px' }}>
                No messages yet.
              </p>
            )}
            {timeline.map((item, idx) => {
              if (item.type === 'user') {
                return (
                  <div key={idx} className="user-message">
                    {item.text}
                  </div>
                )
              } else if (item.type === 'action') {
                return (
                  <div key={idx} className="action-message">
                    <strong>A2UI Action: {item.action}</strong>
                    <pre>{item.text}</pre>
                  </div>
                )
              } else if (item.type === 'text') {
                return (
                  <div key={idx} className="bot-message">
                    {item.text}
                  </div>
                )
              } else if (item.type === 'surface') {
                // Render an A2UI Surface containing multiple UI components
                const surface = rendererRef.current.getSurface(item.surfaceId)
                if (!surface) return null
                return (
                  <div key={item.surfaceId} className="surface-message">
                    {/* @ts-ignore */}
                    <a2ui-surface
                      surface={surface}
                    ></a2ui-surface>
                  </div>
                )
              }
              return null
            })}
            {isRequesting && <div className="loading-spinner">Thinking...</div>}
            <div ref={messagesEndRef} />
          </maui-providers>
        </div>

        {/* --- Chat Input Area --- */}
        <div className="chat-input-area">
          <textarea
            className="chat-textarea"
            placeholder="Type a message..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                handleSend()
              }
            }}
            disabled={isRequesting}
          ></textarea>
          <div className="chat-actions">
            {lastResponseJson && <ResponseViewer json={lastResponseJson} />}
            <button 
              className="import-btn-input" 
              onClick={() => importDialogRef.current?.showModal()}
              style={{ 
                background: 'transparent', 
                color: 'var(--accent, #1a73e8)', 
                border: '1px solid var(--accent, #1a73e8)', 
                padding: '10px 24px', 
                borderRadius: '9999px', 
                fontWeight: 500, 
                cursor: 'pointer',
                marginRight: '8px'
              }}
            >
              Import JSON
            </button>
            <button
              className="send-button"
              onClick={handleSend}
              disabled={isRequesting || !input.trim()}
            >
              {isRequesting ? '...' : 'Send'}
            </button>
          </div>
        </div>
      </aside>

      <dialog ref={importDialogRef} onClick={(e) => { if (e.target === importDialogRef.current) importDialogRef.current.close(); }} style={{ border: 'none', borderRadius: '16px', padding: '0', width: '90%', maxWidth: '600px' }}>
        <div className="dialog-content" style={{ background: 'white', padding: '24px', borderRadius: '16px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>Import A2UI JSON</h2>
          <textarea 
            placeholder='[ { "surfaceUpdate": { ... } }, ... ]'
            value={importJson}
            onChange={(e) => setImportJson(e.target.value)}
            style={{ width: '100%', minHeight: '200px', fontFamily: 'monospace', boxSizing: 'border-box', padding: '12px' }}
          ></textarea>
          <div className="dialog-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '12px' }}>
            <button className="cancel-btn" onClick={() => importDialogRef.current?.close()}>Cancel</button>
            <button className="render-btn" onClick={handleImport} style={{ background: 'var(--p-40, #1a73e8)', color: 'white', border: 'none', padding: '8px 16px', borderRadius: '4px', cursor: 'pointer' }}>
              Render A2UI
            </button>
          </div>
        </div>
      </dialog>
    </div>
  )
}

function ResponseViewer({ json }: { json: string }) {
  const [copied, setCopied] = useState(false);
  const dialogRef = useRef<HTMLDialogElement>(null);

  const handleCopy = () => {
    navigator.clipboard.writeText(json);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <>
      <button 
        className="view-response-btn" 
        onClick={() => dialogRef.current?.showModal()}
        style={{ 
          background: 'transparent', 
          color: 'var(--accent, #1a73e8)', 
          border: '1px solid var(--accent, #1a73e8)', 
          padding: '10px 24px', 
          borderRadius: '9999px', 
          fontWeight: 500, 
          cursor: 'pointer',
          marginRight: '8px'
        }}
      >
        View Last Response
      </button>
      
      <dialog ref={dialogRef} onClick={(e) => { if (e.target === dialogRef.current) dialogRef.current.close(); }} style={{ border: 'none', borderRadius: '16px', padding: '0', width: '90%', maxWidth: '600px' }}>
        <div className="dialog-content" style={{ background: 'white', padding: '24px', borderRadius: '16px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>Last A2UI Response</h2>
          <pre style={{ background: 'rgba(0,0,0,0.05)', padding: '12px', borderRadius: '8px', overflowX: 'auto', maxHeight: '300px', whiteSpace: 'pre-wrap' }}>
            {json || "No response yet."}
          </pre>
          <div className="dialog-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
            <button onClick={() => dialogRef.current?.close()} style={{ background: 'var(--n-90, #e0e0e0)', border: 'none', padding: '8px 16px', borderRadius: '4px', cursor: 'pointer' }}>Close</button>
            <button 
              onClick={handleCopy}
              style={{ background: copied ? '#137333' : 'var(--p-40, #1a73e8)', color: 'white', border: 'none', padding: '8px 16px', borderRadius: '4px', cursor: 'pointer' }}
            >
              {copied ? "Copied!" : "Copy JSON"}
            </button>
          </div>
        </div>
      </dialog>
    </>
  );
}

export default App