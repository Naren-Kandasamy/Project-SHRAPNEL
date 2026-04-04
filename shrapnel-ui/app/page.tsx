"use client"
import { useEffect, useState } from "react"
import { fetchFiles, apiClient } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Progress } from "@/components/ui/progress"
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Download, HardDrive, Upload, X, Search, CheckCircle2, Clock } from "lucide-react"

export default function Dashboard() {
  const [files, setFiles] = useState<any[]>([])

  // Shatter Form States
  const [file, setFile] = useState<File | null>(null)
  const [enableNuke, setEnableNuke] = useState(false) // NEW STATE FOR TOGGLE
  const [expiration, setExpiration] = useState<number | string>(60)
  const [tags, setTags] = useState<string[]>([])
  const [currentTag, setCurrentTag] = useState("")
  const [progress, setProgress] = useState(0)
  const [isUploading, setIsUploading] = useState(false)
  const [shatteredResult, setShatteredResult] = useState<{id: string, fileName: string} | null>(null)
  const [password, setPassword] = useState("")

  // Restore Form States
  const [restoreId, setRestoreId] = useState("")
  const [restorePassword, setRestorePassword] = useState("")
  const [isRestoring, setIsRestoring] = useState(false)
  const [restoreProgress, setRestoreProgress] = useState(0)

  const loadFiles = async () => {
    try {
      const data = await fetchFiles()
      setFiles(data)
    } catch (error) {
      // Backend might be restarting; silently ignore to avoid dev popups
    }
  }

  useEffect(() => {
    loadFiles()
    const interval = setInterval(loadFiles, 30000)
    return () => clearInterval(interval)
  }, [])

  // Tagging Logic
  const handleAddTag = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && currentTag.trim() !== '') {
      e.preventDefault()
      if (!tags.includes(currentTag.trim())) {
        setTags([...tags, currentTag.trim()])
      }
      setCurrentTag("")
    }
  }
  const removeTag = (tagToRemove: string) => setTags(tags.filter(tag => tag !== tagToRemove))

  // Shatter Logic
  const handleUpload = async () => {
    if (!file) return
    setIsUploading(true)
    setShatteredResult(null)

    const query = new URLSearchParams()
    query.append("fileName", file.name)
    if (enableNuke) {
      query.append("expirationMinutes", expiration.toString())
    }
    if (password.trim() !== '') {
      query.append("password", password.trim())
    }
    tags.forEach(tag => query.append("tags", tag))

    try {
      const response = await apiClient.post(`/shatter?${query.toString()}`, file, {
        headers: { "Content-Type": "application/octet-stream" },
        onUploadProgress: (progressEvent) => {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / (progressEvent.total || 1))
          setProgress(percentCompleted)
        }
      })
      
      setShatteredResult({ id: response.data.id, fileName: response.data.fileName })
      loadFiles() 
      
      setFile(null); setTags([]); setProgress(0); setExpiration(60); setEnableNuke(false); setPassword("");
      
      const fileInput = document.getElementById('file-upload') as HTMLInputElement
      if (fileInput) fileInput.value = ''

    } catch (error) {
      // Silently ignore to avoid dev popup
    } finally {
      setIsUploading(false)
    }
  }

  // Restore Logic
  const handleManualRestore = async () => {
    if (restoreId.trim() === "") return
    setIsRestoring(true)
    setRestoreProgress(0)

    try {
        const { startRestore, fetchRestoreStatus } = await import('@/lib/api')
        
        // 1. Send the Async Trigger safely to Spring Boot Native execution
        await startRestore(restoreId.trim(), restorePassword.trim())

        // 2. Poll the integer accurately avoiding DOM memory crashes 
        const pollInterval = window.setInterval(async () => {
            try {
                const status = await fetchRestoreStatus(restoreId.trim())
                if (status >= 0) {
                    setRestoreProgress(status)
                }
                
                if (status === 100) {
                    window.clearInterval(pollInterval)
                    setTimeout(() => {
                        setIsRestoring(false)
                        setRestoreProgress(0)
                        setRestoreId("") 
                        setRestorePassword("")
                    }, 3000) 
                } else if (status === -1) {
                    window.clearInterval(pollInterval)
                    throw new Error("Backend restoration exception flagged")
                }
            } catch (pollErr) {
                window.clearInterval(pollInterval)
                setIsRestoring(false)
            }
        }, 500)

    } catch (error) {
        setIsRestoring(false)
        setRestoreProgress(0)
    }
  }

  return (
    <main className="p-10 max-w-5xl mx-auto space-y-8">
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-3">
          <HardDrive className="w-10 h-10 text-primary" />
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Project SHRAPNEL</h1>
            <p className="text-muted-foreground">Secure Ephemeral File Management System</p>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <a href="http://localhost:5000" target="_blank" rel="noopener noreferrer">
            <Button variant="outline" className="border-primary text-primary hover:bg-primary/10">
              📊 MLflow Analytics
            </Button>
          </a>
        </div>
      </div>

      <Tabs defaultValue="shatter" className="w-full">
        <TabsList className="grid w-full grid-cols-3 mb-8">
          <TabsTrigger value="shatter"><Upload className="w-4 h-4 mr-2"/> Shatter File</TabsTrigger>
          <TabsTrigger value="restore"><Search className="w-4 h-4 mr-2"/> Manual Restore</TabsTrigger>
          <TabsTrigger value="browse"><HardDrive className="w-4 h-4 mr-2"/> Active Files</TabsTrigger>
        </TabsList>

        {/* ======================= SHATTER TAB ======================= */}
        <TabsContent value="shatter">
          <Card>
            <CardHeader>
              <CardTitle>Shatter New Data</CardTitle>
              <CardDescription>Upload a file to fragment it securely across the virtual file system.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              
              {/* Success Alert */}
              {shatteredResult && (
                <div className="p-4 bg-green-50 text-green-900 border border-green-200 rounded-md flex flex-col gap-2">
                  <div className="flex items-center font-semibold">
                    <CheckCircle2 className="w-5 h-5 mr-2 text-green-600" />
                    File Successfully Shattered!
                  </div>
                  <div className="text-sm">
                    <strong>File Name:</strong> {shatteredResult.fileName}<br/>
                    <strong>File ID:</strong> <code className="bg-green-100 px-1 py-0.5 rounded select-all">{shatteredResult.id}</code>
                  </div>
                  <p className="text-xs text-green-700 mt-1">Copy this ID. You will need it to restore the file.</p>
                </div>
              )}

              <div className="space-y-2">
                <label className="text-sm font-medium">Select File</label>
                <Input id="file-upload" type="file" onChange={(e) => setFile(e.target.files?.[0] || null)} disabled={isUploading} />
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">Encryption Password (Optional)</label>
                <Input 
                  type="password"
                  placeholder="Leave blank to drop back to system-wide SHRAPNEL key" 
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={isUploading}
                />
              </div>

			  {/* NUKE TIMER TOGGLE */}
              <div className="flex flex-col p-4 border rounded-md bg-muted/30 transition-all duration-300">
                <div className="flex items-center space-x-2">
                  <input 
                    type="checkbox" 
                    id="enableNuke" 
                    checked={enableNuke} 
                    onChange={(e) => setEnableNuke(e.target.checked)}
                    className="w-4 h-4 rounded border-gray-300 text-primary focus:ring-primary cursor-pointer"
                    disabled={isUploading}
                  />
                  <label htmlFor="enableNuke" className="text-sm font-medium cursor-pointer flex items-center gap-2 select-none">
                    <Clock className="w-4 h-4" /> Enable Nuke Timer (Auto-delete)
                  </label>
                </div>

                {/* Smooth Expansion Wrapper */}
                <div 
                  className={`grid transition-all duration-300 ease-in-out ${
                    enableNuke ? "grid-rows-[1fr] opacity-100 mt-4" : "grid-rows-[0fr] opacity-0"
                  }`}
                >
                  <div className="overflow-hidden">
                    <div className="space-y-2 pl-6 border-l-2 border-primary/30 ml-2 pb-1">
                      <label className="text-sm font-medium text-muted-foreground">Expiration (Minutes)</label>
                      <Input 
                        type="number" 
                        min="1" 
                        value={expiration} 
                        onChange={(e) => setExpiration(e.target.value ? Number(e.target.value) : "")} 
                        disabled={isUploading}
                        className="bg-background max-w-[200px]" // Keeps the input field looking neat
                      />
                    </div>
                  </div>
                </div>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">Tags (Press Enter to add)</label>
                <Input 
                  value={currentTag} 
                  onChange={(e) => setCurrentTag(e.target.value)} 
                  onKeyDown={handleAddTag} 
                  placeholder="e.g. secret, project-x"
                  disabled={isUploading}
                />
                <div className="flex flex-wrap gap-2 mt-2">
                  {tags.map(tag => (
                    <Badge key={tag} className="flex items-center gap-1">
                      {tag} 
                      <X className="w-3 h-3 cursor-pointer" onClick={() => !isUploading && removeTag(tag)} />
                    </Badge>
                  ))}
                </div>
              </div>

              {isUploading && (
                <div className="space-y-2">
                  <div className="flex justify-between text-xs text-muted-foreground pb-1">
                    <span className={progress === 100 ? "text-amber-600 font-semibold animate-pulse" : ""}>
                      {progress < 100 
                        ? "Uploading Payload to Server..." 
                        : "Upload complete! Compiling data cryptographically into shards... DO NOT close this window!"}
                    </span>
                    <span className={progress === 100 ? "text-amber-600 font-bold" : ""}>{progress}%</span>
                  </div>
                  <Progress value={progress} />
                </div>
              )}
            </CardContent>
            <CardFooter>
              <Button 
                className="w-full bg-gradient-to-r from-orange-500 via-red-500 to-purple-600 hover:from-orange-600 hover:via-red-600 hover:to-purple-700 text-white border-0 shadow-lg shadow-orange-500/20 transition-all duration-300 hover:scale-[1.02] active:scale-[0.98]" 
                onClick={handleUpload} 
                disabled={!file || isUploading}
              >
                {isUploading ? "Processing..." : "Execute Shatter Engine"}
              </Button>
            </CardFooter>
          </Card>
        </TabsContent>

        {/* ======================= RESTORE TAB ======================= */}
        <TabsContent value="restore">
          <Card>
            <CardHeader>
              <CardTitle>Restore Fragmented Data</CardTitle>
              <CardDescription>Enter the exact File ID generated during the shatter process to reassemble your file.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">File UUID</label>
                <Input 
                  placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000" 
                  value={restoreId}
                  onChange={(e) => setRestoreId(e.target.value)}
                />
              </div>

              <div className="space-y-2 mt-4">
                <label className="text-sm font-medium">Decryption Password (Optional)</label>
                <Input 
                  type="password"
                  placeholder="Enter password if encrypted uniquely" 
                  value={restorePassword}
                  onChange={(e) => setRestorePassword(e.target.value)}
                  disabled={isRestoring}
                  onKeyDown={(e) => e.key === 'Enter' && handleManualRestore()}
                />
              </div>

              {isRestoring && (
                <div className="space-y-2 mt-6">
                  <div className="flex justify-between text-xs text-muted-foreground pb-1">
                    <span className={restoreProgress === 100 ? "text-green-600 font-semibold" : ""}>
                      {restoreProgress < 100 
                        ? "Downloading and Reassembling File Stream..." 
                        : "Reassembly complete!"}
                    </span>
                    <span className={restoreProgress === 100 ? "text-green-600 font-bold" : ""}>{restoreProgress}%</span>
                  </div>
                  <Progress value={restoreProgress} />
                </div>
              )}
            </CardContent>
            <CardFooter>
              <Button 
                className="w-full bg-gradient-to-r from-purple-600 via-sky-500 to-sky-400 hover:from-purple-700 hover:via-sky-600 hover:to-sky-500 text-white border-0 shadow-lg shadow-sky-500/20 transition-all duration-300 hover:scale-[1.02] active:scale-[0.98]" 
                onClick={handleManualRestore} 
                disabled={!restoreId.trim() || isRestoring}
              >
                {isRestoring ? "Reassembling..." : <><Download className="w-4 h-4 mr-2" /> Execute Reassembly Engine</>}
              </Button>
            </CardFooter>
          </Card>
        </TabsContent>

        {/* ======================= BROWSE TAB ======================= */}
        <TabsContent value="browse">
          <Card>
            <CardHeader>
              <CardTitle>Active File System Overview</CardTitle>
              <CardDescription>View metadata for files currently fragmented in the system.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="border rounded-lg bg-white shadow-sm overflow-hidden">
                <Table>
                  <TableHeader>
                    <TableRow className="bg-muted/50">
                      <TableHead>Filename</TableHead>
                      <TableHead>File ID (UUID)</TableHead>
                      <TableHead>Tags</TableHead>
                      <TableHead>Size (Bytes)</TableHead>
                      <TableHead>Expires At</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {files.map((file) => (
                      <TableRow key={file.id}>
                        <TableCell className="font-medium">{file.fileName}</TableCell>
                        <TableCell className="font-mono text-xs text-muted-foreground">{file.id}</TableCell>
                        <TableCell>
                          <div className="flex gap-1 flex-wrap">
                            {file.tags?.map((tag: string) => (
                              <Badge key={tag} variant="secondary" className="text-[10px]">{tag}</Badge>
                            ))}
                          </div>
                        </TableCell>
                        <TableCell>{file.totalSize}</TableCell>
                        <TableCell>
                          {/* DYNAMIC EXPIRATION DISPLAY */}
                          {file.expirationTime ? new Date(file.expirationTime).toLocaleString() : (
                            <Badge variant="outline" className="text-muted-foreground">Never</Badge>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                    {files.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5} className="text-center text-muted-foreground py-12">
                          No active files found. The system is clean.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

      </Tabs>
    </main>
  )
}
