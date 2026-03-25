"use client"
import { useEffect, useState } from "react"
import { fetchFiles, downloadFile, apiClient } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Progress } from "@/components/ui/progress"
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Download, HardDrive, Upload, X, Search, CheckCircle2, Copy } from "lucide-react"

export default function Dashboard() {
  const [files, setFiles] = useState<any[]>([])

  // Shatter Form States
  const [file, setFile] = useState<File | null>(null)
  const [expiration, setExpiration] = useState(60)
  const [tags, setTags] = useState<string[]>([])
  const [currentTag, setCurrentTag] = useState("")
  const [progress, setProgress] = useState(0)
  const [isUploading, setIsUploading] = useState(false)
  const [shatteredResult, setShatteredResult] = useState<{id: string, fileName: string} | null>(null)

  // Restore Form States
  const [restoreId, setRestoreId] = useState("")

  const loadFiles = async () => {
    try {
      const data = await fetchFiles()
      setFiles(data)
    } catch (error) {
      console.error("Failed to load files", error)
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

    const formData = new FormData()
    formData.append("file", file)
    formData.append("expirationMinutes", expiration.toString())
    tags.forEach(tag => formData.append("tags", tag))

    try {
      const response = await apiClient.post("/shatter", formData, {
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: (progressEvent) => {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / (progressEvent.total || 1))
          setProgress(percentCompleted)
        }
      })
      
      // Capture the returned ID to show the user
      setShatteredResult({ id: response.data.id, fileName: response.data.fileName })
      loadFiles() // Refresh table
      
      // Reset form fields
      setFile(null); setTags([]); setProgress(0); setExpiration(60)
      
      // Note: You must reset the file input element manually in React
      const fileInput = document.getElementById('file-upload') as HTMLInputElement
      if (fileInput) fileInput.value = ''

    } catch (error) {
      console.error("Upload failed", error)
    } finally {
      setIsUploading(false)
    }
  }

  // Restore Logic
  const handleManualRestore = () => {
    if (restoreId.trim() === "") return
    downloadFile(restoreId.trim())
    setRestoreId("") // Clear input after triggering download
  }

  return (
    <main className="p-10 max-w-5xl mx-auto space-y-8">
      <div className="flex items-center gap-3 mb-8">
        <HardDrive className="w-10 h-10 text-primary" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Project SHRAPNEL</h1>
          <p className="text-muted-foreground">Secure Ephemeral File Management System</p>
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
                <label className="text-sm font-medium">Expiration (Minutes)</label>
                <Input type="number" value={expiration} onChange={(e) => setExpiration(Number(e.target.value))} disabled={isUploading} />
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
                  <div className="flex justify-between text-xs text-muted-foreground">
                    <span>Uploading & Shattering...</span>
                    <span>{progress}%</span>
                  </div>
                  <Progress value={progress} />
                </div>
              )}
            </CardContent>
            <CardFooter>
              <Button className="w-full" onClick={handleUpload} disabled={!file || isUploading}>
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
            </CardContent>
            <CardFooter>
              <Button className="w-full" variant="default" onClick={handleManualRestore} disabled={!restoreId.trim()}>
                <Download className="w-4 h-4 mr-2" />
                Execute Reassembly Engine
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
                        <TableCell>{new Date(file.expirationTime).toLocaleString()}</TableCell>
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