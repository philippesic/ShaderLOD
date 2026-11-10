# Pinned native writer entry points

The two integration mixin classes declare required, exact-descriptor Iris 1.8.12 injection/redirect entry points. Error Prone does not recognize their reflective callers. Each class has one exact UnusedMethod directive; the required CallbackInfo parameter has an exact UnusedVariable directive. These are necessary machine directives, not ordinary code comments. No global lint is disabled, and ordinary unused code remains covered by the paired Error Prone specimens.

`IrisConfig.load` restores a pending journal before native parsing. During owned reload, native config/option output is buffered and its exact intended bytes journaled before atomic publication; deletion is journaled likewise. Swallowed native IO failures propagate through the owning scope. Native rendering/link failures remain separate from byte restoration. Unknown foreign edits still refuse recovery.

Required mixin selectors fail startup if their pinned seams stop matching. CPU native-writer tests exercise buffered publication, deletion, failure propagation and preimage recovery; live startup is required to qualify injection application. The approved task authorizes this narrowly scoped integration; editable exception metadata does not authenticate policy approval.
